package domain.io

typealias DdcContent = String
typealias UserId = String
typealias SharedId = String
typealias OwnedStatus = Boolean
typealias SharedIdAndOwnedStatus = Pair<SharedId, OwnedStatus>
typealias DdcContentAndOwnedStatus = Pair<DdcContent, OwnedStatus>

interface DdcSharing {
    var shared: SharedIdAndOwnedStatus?

    fun formatLink(sharedId: SharedId): String
    fun testSharePermission(): Boolean

    /** @return (ddc content, is shared link owned by the user) */
    suspend fun fetchSharedDdc(sharedId: SharedId): Result<DdcContentAndOwnedStatus>
    suspend fun registerUser(): Result<UserId>
    suspend fun shareNewDdc(content: DdcContent): Result<SharedId>
    suspend fun overwriteSharedDdc(sharedId: SharedId, content: DdcContent): Result<SharedId>
}