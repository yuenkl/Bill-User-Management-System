package com.bill.usermanagmentsystem.platform

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

class AndroidSqlDriverFactory(
    private val context: Context,
) : SqlDriverFactory {
    override fun create(
        schema: SqlSchema<QueryResult.Value<Unit>>,
        name: String,
    ): SqlDriver = AndroidSqliteDriver(
        schema = schema,
        context = context,
        name = name,
    )
}
