-keepattributes *Annotation*
-dontwarn kotlin.**

# Retrofit uses Gson reflection for the mobile contract, employee API DTOs and
# encrypted local drafts. These models intentionally use their Kotlin property
# names as JSON field names, so R8 must not rename or remove their fields.
# Without this rule the release APK sends payloads such as {"a":...,"b":...}
# instead of {"username":...,"password":...}, while debug builds still pass.
-keep class com.eagleslab.yingge.data.contract.** { *; }
-keep class com.eagleslab.yingge.data.**Dto { *; }
-keep class com.eagleslab.yingge.data.StoredPushRegistration { *; }
-keep class com.eagleslab.yingge.model.CustomerDraft { *; }
-keep class com.eagleslab.yingge.model.DailySummaryDraft { *; }
