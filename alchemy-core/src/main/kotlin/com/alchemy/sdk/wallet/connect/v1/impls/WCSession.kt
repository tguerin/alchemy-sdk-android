package com.alchemy.sdk.wallet.connect.v1.impls

import com.alchemy.sdk.wallet.connect.v1.Session
import com.alchemy.sdk.wallet.connect.v1.types.extractSessionParams
import com.alchemy.sdk.wallet.connect.v1.types.intoMap
import com.alchemy.sdk.wallet.connect.v1.nullOnThrow
import java.util.Collections
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Suppress("UNCHECKED_CAST")
class WCSession(
    private val config: Session.FullyQualifiedConfig,
    private val payloadAdapter: Session.PayloadAdapter,
    private val payloadEncryption: Session.PayloadEncryption,
    private val sessionStore: WCSessionStore,
    private val messageLogger: Session.MessageLogger? = null,
    transportBuilder: Session.Transport.Builder,
    clientMeta: Session.PeerMeta,
    clientId: String? = null
) : Session {

    private val keyLock = Any()

    // Persisted state
    private var currentKey: String

    private var approvedAccounts: List<String>? = null
    private var chainId: Long? = null
    private var handshakeId: Long? = null
    private var peerId: String? = null
    private var peerMeta: Session.PeerMeta? = null

    private val clientData: Session.PeerData

    // Getters
    private val encryptionKey: String
        get() = currentKey

    private val decryptionKey: String
        get() = currentKey

    // Non-persisted state
    private val transport = transportBuilder.build(config.bridge, ::handleStatus, ::handleMessage)
    private val requests: MutableMap<Long, (Session.MethodCall.Response) -> Unit> =
        ConcurrentHashMap()
    private val sessionCallbacks: MutableSet<Session.Callback> =
        Collections.newSetFromMap(ConcurrentHashMap<Session.Callback, Boolean>())

    init {
        currentKey = config.key
        clientData = sessionStore.load(config.handshakeTopic)?.let {
            currentKey = it.currentKey
            approvedAccounts = it.approvedAccounts
            chainId = it.chainId
            handshakeId = it.handshakeId
            peerId = it.peerData?.id
            peerMeta = it.peerData?.peerMeta
            if (clientId != null && clientId != it.clientData.id)
                throw IllegalArgumentException("Provided clientId is different from stored clientId")
            it.clientData
        } ?: run {
            Session.PeerData(clientId ?: UUID.randomUUID().toString(), clientMeta)
        }
        storeSession()
    }

    override fun addCallback(cb: Session.Callback) {
        sessionCallbacks.add(cb)
    }

    override fun removeCallback(cb: Session.Callback) {
        sessionCallbacks.remove(cb)
    }

    override fun clearCallbacks() {
        sessionCallbacks.clear()
    }

    private fun propagateToCallbacks(action: Session.Callback.() -> Unit) {
        sessionCallbacks.forEach {
            try {
                it.action()
            } catch (t: Throwable) {
                // If error propagation fails, don't try again
                nullOnThrow { it.onStatus(Session.Status.Error(t)) }
            }
        }
    }

    override fun peerMeta(): Session.PeerMeta? = peerMeta

    override fun approvedAccounts(): List<String>? = approvedAccounts

    override fun chainId(): Long? = chainId

    override fun init() {
        if (transport.connect()) {
            // Register for all messages for this client
            val message = Session.Transport.Message(
                config.handshakeTopic, "sub", ""
            )
            transport.send(message)
            messageLogger?.log(message, isOwnMessage = true)
        }
    }

    override fun offer() {
        if (transport.connect()) {
            val requestId = createCallId()
            send(
                Session.MethodCall.SessionRequest(requestId, clientData),
                topic = config.handshakeTopic,
                callback = { resp ->
                    (resp.result as? Map<String, *>)?.extractSessionParams()?.let { params ->
                        peerId = params.peerData?.id
                        peerMeta = params.peerData?.peerMeta
                        updateSession(params)
                        propagateToCallbacks { onStatus(if (params.approved) Session.Status.Approved else Session.Status.Closed) }
                    }
                })
            handshakeId = requestId
        }
    }

    private fun updateSession(params: Session.SessionParams) {
        approvedAccounts = params.accounts
        chainId = params.chainId
        storeSession()
    }

    override fun approve(accounts: List<String>, chainId: Long) {
        val handshakeId = handshakeId ?: return
        approvedAccounts = accounts
        this.chainId = chainId
        // We should not use classes in the Response, since this will not work with proguard
        val params = Session.SessionParams(true, chainId, accounts, clientData).intoMap()
        send(Session.MethodCall.Response(handshakeId, params))
        storeSession()
        propagateToCallbacks { onStatus(Session.Status.Approved) }
    }

    override fun update(accounts: List<String>, chainId: Long) {
        val params = Session.SessionParams(true, chainId, accounts, clientData)
        send(Session.MethodCall.SessionUpdate(createCallId(), params))
    }

    override fun reject() {
        handshakeId?.let {
            // We should not use classes in the Response, since this will not work with proguard
            val params = Session.SessionParams(false, null, null, null).intoMap()
            send(Session.MethodCall.Response(it, params))
        }
        endSession()
    }

    override fun approveRequest(id: Long, response: Any) {
        send(Session.MethodCall.Response(id, response))
    }

    override fun rejectRequest(id: Long, errorCode: Long, errorMsg: String) {
        send(
            Session.MethodCall.Response(
                id,
                result = null,
                error = Session.Error(errorCode, errorMsg)
            )
        )
    }

    override fun performMethodCall(
        call: Session.MethodCall,
        callback: ((Session.MethodCall.Response) -> Unit)?
    ) {
        send(call, callback = callback)
    }

    private fun handleStatus(status: Session.Transport.Status) {
        when (status) {
            Session.Transport.Status.Connected -> {
                // Register for all messages for this client
                val message = Session.Transport.Message(
                    clientData.id, "sub", ""
                )
                transport.send(message)
                messageLogger?.log(message, isOwnMessage = true)
            }
            Session.Transport.Status.Disconnected -> {
                // no-op
            }
            is Session.Transport.Status.Error -> {
                // no-op
            }
        }
        propagateToCallbacks {
            onStatus(
                when (status) {
                    Session.Transport.Status.Connected -> Session.Status.Connected
                    Session.Transport.Status.Disconnected -> Session.Status.Disconnected
                    is Session.Transport.Status.Error -> Session.Status.Error(
                        Session.TransportError(
                            status.throwable
                        )
                    )
                }
            )
        }
    }

    private fun handleMessage(message: Session.Transport.Message) {
        if (message.type != "pub") return
        val data: Session.MethodCall
        synchronized(keyLock) {
            try {
                val decryptedPayload = payloadEncryption.decrypt(message.payload, decryptionKey)
                data = payloadAdapter.parse(decryptedPayload)
                messageLogger?.log(message.copy(payload = decryptedPayload), isOwnMessage = false)
            } catch (e: Exception) {
                handlePayloadError(e)
                return
            }
        }
        var accountToCheck: String? = null
        when (data) {
            is Session.MethodCall.SessionRequest -> {
                handshakeId = data.id
                peerId = data.peer.id
                peerMeta = data.peer.peerMeta
                storeSession()
            }
            is Session.MethodCall.SessionUpdate -> {
                if (!data.params.approved) {
                    endSession()
                } else {
                    updateSession(data.params)
                    propagateToCallbacks {
                        onStatus(Session.Status.Updated)
                    }
                }
            }
            is Session.MethodCall.SendTransaction -> {
                accountToCheck = data.from
            }
            is Session.MethodCall.SignMessage -> {
                accountToCheck = data.address
            }
            is Session.MethodCall.Response -> {
                val callback = requests[data.id] ?: return
                callback(data)
            }
            is Session.MethodCall.Custom -> {
                // no-op
            }
        }

        if (accountToCheck?.let { accountCheck(data.id(), it) } != false) {
            propagateToCallbacks { onMethodCall(data) }
        }
    }

    private fun accountCheck(id: Long, address: String): Boolean {
        approvedAccounts?.find { it.equals(address, ignoreCase = true) } ?: run {
            handlePayloadError(Session.MethodCallException.InvalidAccount(id, address))
            return false
        }
        return true
    }

    private fun handlePayloadError(e: Exception) {
        propagateToCallbacks { Session.Status.Error(e) }
        (e as? Session.MethodCallException)?.let {
            rejectRequest(it.id, it.code, it.message ?: "Unknown error")
        }
    }

    private fun endSession() {
        sessionStore.remove(config.handshakeTopic)
        approvedAccounts = null
        chainId = null
        internalClose()
        propagateToCallbacks { onStatus(Session.Status.Closed) }
    }

    private fun storeSession() {
        sessionStore.store(
            config.handshakeTopic,
            WCSessionStore.State(
                config,
                clientData,
                peerId?.let { Session.PeerData(it, peerMeta) },
                handshakeId,
                currentKey,
                approvedAccounts,
                chainId
            )
        )
    }

    // Returns true if method call was handed over to transport
    private fun send(
        msg: Session.MethodCall,
        topic: String? = peerId,
        callback: ((Session.MethodCall.Response) -> Unit)? = null
    ): Boolean {
        topic ?: return false

        val payload: String
        val unencryptedPayload: String
        synchronized(keyLock) {
            unencryptedPayload = payloadAdapter.prepare(msg)
            payload = payloadEncryption.encrypt(unencryptedPayload, encryptionKey)
        }
        callback?.let {
            requests[msg.id()] = callback
        }
        val message = Session.Transport.Message(topic, "pub", payload)
        transport.send(message)
        messageLogger?.log(message.copy(payload = unencryptedPayload), isOwnMessage = true)
        return true
    }

    private fun createCallId() = System.currentTimeMillis() * 1000 + Random().nextInt(999)

    private fun internalClose() {
        transport.close()
    }

    override fun kill() {
        val params = Session.SessionParams(false, null, null, null)
        send(Session.MethodCall.SessionUpdate(createCallId(), params))
        endSession()
    }
}

interface WCSessionStore {
    fun load(id: String): State?

    fun store(id: String, state: State)

    fun remove(id: String)

    fun list(): List<State>

    data class State(
        val config: Session.FullyQualifiedConfig,
        val clientData: Session.PeerData,
        val peerData: Session.PeerData?,
        val handshakeId: Long?,
        val currentKey: String,
        val approvedAccounts: List<String>?,
        val chainId: Long?
    )
}
