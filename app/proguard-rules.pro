# R8 rules for the release build. AndroidX (Room, Compose, Lifecycle) and kotlinx.coroutines ship their own
# consumer rules, and the app uses no reflection or serialization, so nothing else needs to be kept.
# Category and TransactionType are stored by enum name; R8 keeps Enum.name() values, so that is safe too.

# Readable stack traces once deobfuscated with the R8 mapping file (app/build/outputs/mapping/release/).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
