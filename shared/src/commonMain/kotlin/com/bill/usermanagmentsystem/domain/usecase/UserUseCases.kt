package com.bill.usermanagmentsystem.domain.usecase

import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.DeletedUserUndo
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.repository.PageLoadResult
import com.bill.usermanagmentsystem.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow

fun interface ObserveUsers {
    operator fun invoke(): Flow<List<UserRecord>>
}

fun interface RefreshUsers {
    suspend operator fun invoke(): Result<Unit>
}

fun interface LoadNextUsersPage {
    suspend operator fun invoke(): Result<PageLoadResult>
}

fun interface AddUser {
    suspend operator fun invoke(input: AddUserInput): Result<String>
}

fun interface DeleteUser {
    suspend operator fun invoke(localId: String): Result<DeletedUserUndo>
}

class DefaultDeleteUser(
    private val repository: UserRepository,
) : DeleteUser {
    override suspend fun invoke(localId: String): Result<DeletedUserUndo> = repository.deleteImmediately(localId)
}

fun interface UndoUserDeletion {
    suspend operator fun invoke(input: AddUserInput): Result<String>
}
