package com.hiroshi.moneyapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hiroshi.moneyapp.R
import com.hiroshi.moneyapp.data.db.BudgetPeriod
import com.hiroshi.moneyapp.data.db.CategoryEntity
import com.hiroshi.moneyapp.data.db.Frequency
import com.hiroshi.moneyapp.data.db.TxType
import com.hiroshi.moneyapp.data.db.WalletType

/** Built-in categories show a translated name; user categories show what the user typed. */
@Composable
fun categoryLabel(name: String?, builtinKey: String?): String {
    val res = when (builtinKey) {
        "food" -> R.string.cat_food
        "groceries" -> R.string.cat_groceries
        "transport" -> R.string.cat_transport
        "shopping" -> R.string.cat_shopping
        "bills" -> R.string.cat_bills
        "entertainment" -> R.string.cat_entertainment
        "health" -> R.string.cat_health
        "education" -> R.string.cat_education
        "family" -> R.string.cat_family
        "gifts" -> R.string.cat_gifts
        "fees" -> R.string.cat_fees
        "other_expense" -> R.string.cat_other_expense
        "salary" -> R.string.cat_salary
        "bonus" -> R.string.cat_bonus
        "business" -> R.string.cat_business
        "interest" -> R.string.cat_interest
        "gift_received" -> R.string.cat_gift_received
        "other_income" -> R.string.cat_other_income
        else -> null
    }
    return when {
        res != null -> stringResource(res)
        name != null -> name
        else -> stringResource(R.string.uncategorized)
    }
}

@Composable
fun categoryLabel(c: CategoryEntity) = categoryLabel(c.name, c.builtinKey)

@Composable
fun walletTypeLabel(t: WalletType) = stringResource(
    when (t) {
        WalletType.CASH -> R.string.wallet_type_cash
        WalletType.BANK -> R.string.wallet_type_bank
        WalletType.EWALLET -> R.string.wallet_type_ewallet
        WalletType.CREDIT_CARD -> R.string.wallet_type_credit
        WalletType.OTHER -> R.string.wallet_type_other
    }
)

@Composable
fun txTypeLabel(t: TxType) = stringResource(
    when (t) {
        TxType.EXPENSE -> R.string.type_expense
        TxType.INCOME -> R.string.type_income
        TxType.TRANSFER -> R.string.type_transfer
    }
)

@Composable
fun frequencyLabel(f: Frequency) = stringResource(
    when (f) {
        Frequency.DAILY -> R.string.freq_daily
        Frequency.WEEKLY -> R.string.freq_weekly
        Frequency.MONTHLY -> R.string.freq_monthly
        Frequency.YEARLY -> R.string.freq_yearly
    }
)

@Composable
fun budgetPeriodLabel(p: BudgetPeriod) = stringResource(
    when (p) {
        BudgetPeriod.WEEKLY -> R.string.period_weekly
        BudgetPeriod.MONTHLY -> R.string.period_monthly
    }
)
