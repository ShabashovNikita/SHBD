package app.shbd.models

import androidx.annotation.StringRes
import app.shbd.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Gender {
    @SerialName("male") MALE,
    @SerialName("female") FEMALE,
    @SerialName("other") OTHER;

    @get:StringRes
    val displayNameRes: Int get() = when (this) {
        MALE -> R.string.gender_male
        FEMALE -> R.string.gender_female
        OTHER -> R.string.gender_other
    }
}
