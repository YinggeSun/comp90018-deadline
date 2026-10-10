package com.comp90018.deadline.data.remote

import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException

/** True when [this] means the server could not be reached, as opposed to a refusal or a bug. */
internal fun Throwable.isNetworkError(): Boolean =
    this is FirebaseNetworkException || this is TimeoutCancellationException || this is IOException
