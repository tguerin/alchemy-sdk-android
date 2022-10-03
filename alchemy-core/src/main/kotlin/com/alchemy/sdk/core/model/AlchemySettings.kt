package com.alchemy.sdk.core.model

import com.alchemy.sdk.core.model.core.Network
import com.alchemy.sdk.core.util.Constants

data class AlchemySettings(
    /** The Alchemy API key that can be found in the Alchemy dashboard. */
    val apiKey: String = Constants.DEFAULT_ALCHEMY_API_KEY,

    /**
     * The name of the network. Once configured, the network cannot be changed. To
     * use a different network, instantiate a new `Alchemy` instance
     */
    val network: Network = Constants.DEFAULT_NETWORK,

    /** The maximum number of retries to attempt if a request fails. Defaults to 5. Not used for now*/
    val maxRetries: Int = Constants.DEFAULT_MAX_RETRIES,

    /**
     * Optional URL endpoint to use for all requests. Setting this field will
     * override the URL generated by the {@link network} and {@link apiKey} fields.
     *
     * This field is useful for testing or for using a custom node endpoint. Note
     * that not all methods will work with custom URLs.
     */
    val url: String? = null
)