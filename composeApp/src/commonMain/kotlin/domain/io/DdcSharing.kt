package domain.io

typealias DdcContent = String
typealias UserId = String
typealias SharedId = String

interface DdcSharing {
    suspend fun fetchSharedDdc(sharedId: SharedId): DdcContent?
    suspend fun registerSharer(): UserId?
    fun testSharePermission(): Boolean
    suspend fun shareNewDdc(content: DdcContent): SharedId?
    suspend fun overwriteSharedDdc(sharedId: SharedId, content: DdcContent): SharedId?
}