package com.scenevibe.tvcompanionpoc;

/**
 * Observed input ownership, separate from visibility and commentary admission.
 * A focus request is not a grant. Only the native window callback can confirm it;
 * removal must release input before CLOSED is reported. No global key hook exists.
 */
final class DeveloperFocusState {
    enum State { PASSIVE, INTERACTIVE_REQUESTED, INTERACTIVE_CONFIRMED, RELEASING, CLOSED }
    private State state=State.CLOSED;
    /** Expose observed control-window ownership without granting any multimedia authority. */
    State state(){return state;}
    /** A visible monitor is not an input receiver. */
    void passive(){state=State.PASSIVE;}
    /** Record only the user's requested native transition. */
    void request(){state=State.INTERACTIVE_REQUESTED;}
    /** Only a real window-focus callback confirms input ownership. */
    void gained(){if(state==State.INTERACTIVE_REQUESTED)state=State.INTERACTIVE_CONFIRMED;}
    /** Unexpected loss releases the UI to a passive policy, never a hidden key trap. */
    void lost(){if(state==State.INTERACTIVE_CONFIRMED || state==State.INTERACTIVE_REQUESTED)state=State.PASSIVE;}
    /** Announce release before changing flags or removing the root. */
    void releasing(){state=State.RELEASING;}
    /** Confirm that the retired window no longer has an attached input capability. */
    void released(){state=State.CLOSED;}
}
