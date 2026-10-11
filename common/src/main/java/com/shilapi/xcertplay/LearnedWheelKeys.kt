package com.shilapi.xcertplay

/**
 * Fallback for the broadcast key-learning object used on Leapmotor builds.
 *
 * Broadcast key learning (capture -> storage -> UI) is **not part of upstream DiPlay** — it is a
 * self-contained feature alongside the wheel-key transport, and porting it is a separate job from
 * getting the wheel keys working.
 *
 * This stub keeps [LeapmotorMediaKeys] compiling while that is in progress: every hook answers
 * "nothing is learnt", so keys always take the generic Leapmotor mapping. Delete this file once the
 * real implementation lands; nothing else references it by name.
 *
 * The four signatures below are the exact contract [LeapmotorMediaKeys.dispatch] relies on.
 */
internal object LearnedWheelKeys {

    /** Whether the user is currently teaching DiPlay what a button means. */
    fun isCapturing(): Boolean = false

    /**
     * Offer a raw key id to the capture flow. Returns true when the learner consumed it and the
     * caller must not forward the press to CarPlay.
     */
    fun onKey(id: String): Boolean = false

    /**
     * Whether a learnt binding already owns this broadcast action, in which case forwarding here
     * would double-fire alongside the learnt handler.
     */
    fun broadcastBindingConsumes(action: String): Boolean = false

    /** The CarPlay media button a learnt binding maps [action] to, or null when nothing is learnt. */
    fun learnedCarButton(action: String): Int? = null
}
