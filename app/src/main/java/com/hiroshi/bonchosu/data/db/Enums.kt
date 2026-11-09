package com.hiroshi.bonchosu.data.db

// Room stores enums as their name (TEXT), so the SQL in Daos.kt compares against 'EXPENSE' etc.

enum class WalletType { CASH, BANK, EWALLET, CREDIT_CARD, OTHER }
enum class CategoryType { EXPENSE, INCOME }
enum class TxType { EXPENSE, INCOME, TRANSFER }
enum class TxSource { MANUAL, NOTIFICATION, IMPORT, RECURRING, RECEIPT }
enum class TxStatus { CONFIRMED, PENDING_REVIEW }
enum class BudgetPeriod { WEEKLY, MONTHLY }
enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }
