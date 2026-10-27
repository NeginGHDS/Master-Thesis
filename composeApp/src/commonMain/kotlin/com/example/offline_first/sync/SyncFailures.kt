package com.example.offline_first.sync

/**
 * The two kinds of failure an upload can meet, and the reason the app has both a `PENDING` and a
 * `FAILED` status.
 *
 * They are told apart because they mean completely different things to the user: "no signal, I will
 * send it as soon as you are back" is not the same message as "the server looked at this and said
 * no". [SyncManager] maps the first to `PENDING` and the second to `FAILED`; a store implementation
 * is what decides which one a given error is.
 */

/**
 * No route to the backend: airplane mode, no signal, a timeout, or a request dropped on a weak link.
 * Nothing is wrong with the report itself, so it stays `PENDING` and the next sync retries it.
 */
class NetworkUnavailableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * The backend was reached and refused the operation — a security rule denying the write, an
 * exhausted quota, a malformed document, or a Firestore outage. The device is online, so waiting
 * for connectivity will not help; the report is marked `FAILED` so the user can see that the
 * *server* rejected it rather than assuming it is still queued.
 */
class BackendUnavailableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
