package domain.io

typealias DdcContent = String
typealias UserId = String
typealias SharedId = String
typealias OwnedStatus = Boolean
typealias SharedIdAndOwnedStatus = Pair<SharedId, OwnedStatus>
typealias DdcContentAndOwnedStatus = Pair<DdcContent, OwnedStatus>

interface DdcSharing {
    var shared: SharedIdAndOwnedStatus?
    /** @return (ddc content, is shared link owned by the user) */
    suspend fun fetchSharedDdc(sharedId: SharedId): DdcContentAndOwnedStatus?
    suspend fun registerUser(): UserId?
    fun testSharePermission(): Boolean
    suspend fun shareNewDdc(content: DdcContent): SharedId?
    suspend fun overwriteSharedDdc(sharedId: SharedId, content: DdcContent): SharedId?
    fun formatLink(sharedId: SharedId): String
}