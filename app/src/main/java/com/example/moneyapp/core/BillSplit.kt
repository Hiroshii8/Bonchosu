package com.hiroshi.moneyapp.core

/**
 * Splits a bill in whole rupiah. Every amount is shared by "largest remainder",
 * so the shares always add up to exactly the items plus the charges.
 */
object BillSplit {

    /** [units] = person id → portions eaten. 2 and 1 means two thirds and one third of the line total. */
    data class Item(val id: Long, val lineTotal: Long, val units: Map<Long, Int>)

    /** [amount] is negative for discounts. Uses the printed amount, never a recalculated percentage. */
    data class Charge(val amount: Long, val equally: Boolean)

    data class Share(val itemAmounts: Map<Long, Long>, val itemsTotal: Long, val chargesTotal: Long) {
        val total get() = itemsTotal + chargesTotal
    }

    fun split(people: List<Long>, items: List<Item>, charges: List<Charge>): Map<Long, Share> {
        val itemAmounts = people.associateWith { mutableMapOf<Long, Long>() }
        for (item in items) {
            allocate(item.lineTotal, people.associateWith { (item.units[it] ?: 0).toLong() }).forEach { (person, amount) ->
                if (amount != 0L) itemAmounts.getValue(person)[item.id] = amount
            }
        }
        val itemsTotal = people.associateWith { itemAmounts.getValue(it).values.sum() }

        val chargesTotal = people.associateWith { 0L }.toMutableMap()
        // Equal charges are shared by the people who ordered something, not by anyone just listed on the bill.
        val ordered = itemsTotal.filterValues { it > 0 }.keys
        for (charge in charges) {
            val weights = people.associateWith { person ->
                if (charge.equally) (if (person in ordered) 1L else 0L) else itemsTotal.getValue(person)
            }
            allocate(charge.amount, weights).forEach { (person, amount) -> chargesTotal[person] = chargesTotal.getValue(person) + amount }
        }
        return people.associateWith { Share(itemAmounts.getValue(it), itemsTotal.getValue(it), chargesTotal.getValue(it)) }
    }

    /** Item id → what this person's portion really costs: the item plus its part of their fees. Adds up to [Share.total]. */
    fun orderCosts(share: Share): Map<Long, Long> {
        val fees = allocate(share.chargesTotal, share.itemAmounts)
        return share.itemAmounts.mapValues { (item, amount) -> amount + fees.getValue(item) }
    }

    /** Shares [amount] by [weights]. With all weights zero it's shared equally, so no rupiah gets lost. */
    fun allocate(amount: Long, weights: Map<Long, Long>): Map<Long, Long> {
        if (weights.isEmpty()) return emptyMap()
        val effective = if (weights.values.sum() > 0) weights else weights.mapValues { 1L }
        val totalWeight = effective.values.sum()
        val size = kotlin.math.abs(amount)
        val floors = effective.mapValues { (_, weight) -> Math.multiplyExact(size, weight) / totalWeight }
        val left = (size - floors.values.sum()).toInt()
        // The leftover rupiah go to the biggest fractions; ties keep the listed order.
        val lucky = effective.keys.sortedByDescending { Math.multiplyExact(size, effective.getValue(it)) % totalWeight }.take(left).toSet()
        return floors.mapValues { (person, floor) ->
            val share = floor + if (person in lucky) 1 else 0
            if (amount < 0) -share else share
        }
    }
}
