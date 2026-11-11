package org.emlii.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.random.Random

/**
 * !trade — the TERMSIM stock-trading simulator, ported from the desktop/web tools/trading.py.
 * One shared portfolio per phone (saved to trading.json), since there is only one user here.
 */
class TradingTool(private val file: File) {
    private data class Stock(var price: Double, val sector: String, val vol: Double, val history: MutableList<Double>)

    private val startingCash = 10_000.0
    private var cash = startingCash
    private val holdings = mutableMapOf<String, Int>()
    private val avgCost = mutableMapOf<String, Double>()
    private var day = 1
    private var realizedPnl = 0.0
    private var lastNews: List<String> = emptyList()
    private val netWorthHistory = mutableListOf(startingCash)

    private fun startingMarket() = linkedMapOf(
        "NEBX" to Stock(142.50, "Tech", 0.020, mutableListOf(142.50)),
        "QBIT" to Stock(88.10, "Tech", 0.030, mutableListOf(88.10)),
        "SOLR" to Stock(34.75, "Energy", 0.025, mutableListOf(34.75)),
        "PTRL" to Stock(61.20, "Energy", 0.018, mutableListOf(61.20)),
        "VBNK" to Stock(210.00, "Finance", 0.012, mutableListOf(210.00)),
        "CRDX" to Stock(45.30, "Finance", 0.015, mutableListOf(45.30)),
        "MEDV" to Stock(97.60, "Health", 0.014, mutableListOf(97.60)),
        "GENZ" to Stock(22.40, "Health", 0.022, mutableListOf(22.40)),
        "SHPX" to Stock(15.80, "Retail", 0.020, mutableListOf(15.80)),
        "URBN" to Stock(53.15, "Retail", 0.017, mutableListOf(53.15)),
        "DOGX" to Stock(0.85, "Crypto", 0.080, mutableListOf(0.85)),
        "LUNR" to Stock(12.40, "Crypto", 0.070, mutableListOf(12.40))
    )

    private var market = startingMarket()

    // (sector, direction, headline) — "" sector = affects the whole market
    private val newsEvents = listOf(
        Triple("Tech", 1, "A surprise chip breakthrough boosts investor confidence in Tech."),
        Triple("Tech", -1, "Regulators announce a new antitrust probe into Tech giants."),
        Triple("Energy", 1, "Oil prices spike after supply disruption fears."),
        Triple("Energy", -1, "New renewable subsidies pressure traditional Energy names."),
        Triple("Finance", 1, "Central bank hints at rate cuts, lifting Finance stocks."),
        Triple("Finance", -1, "Credit downgrade warning rattles the Finance sector."),
        Triple("Health", 1, "A major drug trial reports unexpectedly strong results."),
        Triple("Health", -1, "Patent litigation clouds outlook for Health companies."),
        Triple("Retail", 1, "Holiday sales figures crush analyst expectations."),
        Triple("Retail", -1, "Consumer spending data comes in weaker than forecast."),
        Triple("Crypto", 1, "A major exchange announces new institutional adoption."),
        Triple("Crypto", -1, "A large hack triggers a sell-off across Crypto tokens."),
        Triple("", 1, "Broad market rallies on cooling inflation data."),
        Triple("", -1, "Markets slide amid recession fears.")
    )

    init { load() }

    private fun netWorth(): Double =
        cash + holdings.entries.sumOf { (t, q) -> q * (market[t]?.price ?: 0.0) }

    private fun round2(v: Double) = Math.round(v * 100) / 100.0

    private fun applyMove(s: Stock, pct: Double) {
        s.price = max(0.05, round2(s.price * (1 + pct)))
        s.history.add(s.price)
        if (s.history.size > 200) s.history.removeAt(0)
    }

    fun advanceDay(days: Int): String {
        val heads = mutableListOf<String>()
        repeat(days) {
            day++
            if (Random.nextDouble() < 0.55) {
                val (sector, dir, headline) = newsEvents.random()
                heads.add(headline)
                for (s in market.values) if (sector.isEmpty() || s.sector == sector) {
                    applyMove(s, dir * (0.03 + Random.nextDouble() * 0.06))
                }
            }
            for (s in market.values) applyMove(s, (Random.nextDouble() * 2 - 1) * s.vol)
            netWorthHistory.add(netWorth())
            if (netWorthHistory.size > 200) netWorthHistory.removeAt(0)
        }
        lastNews = if (days > 1) heads.takeLast(3) else heads
        save()
        val header = "=== Day $day begins ===" + (if (days > 1) "  ($days days passed)" else "")
        return "$header\n${newsText()}\n\n${pricesText()}"
    }

    private fun spark(values: List<Double>, width: Int = 12): String {
        if (values.size < 2) return ""
        val v = values.takeLast(width)
        val blocks = "▁▂▃▄▅▆▇█"
        val lo = v.min()
        val hi = v.max()
        val span = if (hi - lo == 0.0) 1.0 else hi - lo
        return v.joinToString("") { blocks[(((it - lo) / span) * (blocks.length - 1)).toInt().coerceIn(0, blocks.length - 1)].toString() }
    }

    fun pricesText(): String {
        val sb = StringBuilder("Day $day · Market\n")
        sb.append(String.format("%-6s%-8s%9s%9s  TREND\n", "TICK", "SECTOR", "PRICE", "CHG"))
        for ((t, s) in market) {
            val h = s.history
            val chg = if (h.size >= 2 && h[h.size - 2] != 0.0) (h.last() - h[h.size - 2]) / h[h.size - 2] * 100 else 0.0
            val arrow = if (chg > 0) "▲" else if (chg < 0) "▼" else "·"
            val chgText = "$arrow${"%.2f".format(Math.abs(chg))}%"
            sb.append(String.format("%-6s%-8s%9.2f%9s  %s\n", t, s.sector, s.price, chgText, spark(h)))
        }
        return sb.toString().trim()
    }

    fun portfolioText(): String {
        val nw = netWorth()
        val sb = StringBuilder("Portfolio · Day $day\nCash: \$${"%,.2f".format(cash)}\n")
        if (holdings.isNotEmpty()) {
            sb.append(String.format("\n%-6s%5s%9s%9s%11s%11s\n", "TICK", "QTY", "AVG", "PRICE", "VALUE", "P/L"))
            for ((t, q) in holdings) {
                val price = market[t]?.price ?: 0.0
                val avg = avgCost[t] ?: 0.0
                sb.append(String.format("%-6s%5d%9.2f%9.2f%11.2f%+11.2f\n", t, q, avg, price, price * q, (price - avg) * q))
            }
        } else {
            sb.append("\n(no holdings)\n")
        }
        val sign = if (realizedPnl >= 0) "+" else "-"
        sb.append("\nRealized P/L: $sign\$${"%,.2f".format(Math.abs(realizedPnl))}\n")
        val pct = (nw - startingCash) / startingCash * 100
        val pctArrow = if (pct >= 0) "▲" else "▼"
        sb.append("Net worth: \$${"%,.2f".format(nw)}  (started \$${startingCash.toInt()}, $pctArrow${"%.2f".format(Math.abs(pct))}%)\n")
        sb.append("History: ${spark(netWorthHistory, 30)}")
        return sb.toString()
    }

    fun newsText(): String =
        if (lastNews.isEmpty()) "No major news today." else "Headlines\n" + lastNews.joinToString("\n") { "  • $it" }

    fun chartText(ticker: String): String {
        val t = ticker.uppercase()
        val s = market[t] ?: return "No such ticker: $t"
        if (s.history.size < 2) return "$t has no history yet — try !trade next first."
        val h = s.history.takeLast(40)
        val lo = h.min()
        val hi = h.max()
        val span = if (hi - lo == 0.0) max(hi, 0.001) * 0.001 else hi - lo
        val cur = h.last()
        val chg = if (h.first() != 0.0) (cur - h.first()) / h.first() * 100 else 0.0
        val sb = StringBuilder(
            "$t price history   \$${"%,.2f".format(cur)}   ${if (chg >= 0) "▲" else "▼"}${"%.2f".format(Math.abs(chg))}%" +
                "   hi \$${"%,.2f".format(hi)} / lo \$${"%,.2f".format(lo)}\n\n"
        )
        for (r in 10 downTo 0) {
            val thr = lo + span * (r / 10.0)
            val row = h.indices.joinToString("") { i -> if (h[i] >= thr) (if (i == h.size - 1) "●" else "│") else " " }
            sb.append(String.format("%8.2f %s\n", thr, row))
        }
        return sb.toString().trim()
    }

    fun buy(ticker: String, qtyWord: String): String {
        val t = ticker.uppercase()
        val s = market[t] ?: return "No such ticker: $t"
        val qty = if (qtyWord.equals("max", true)) (cash / s.price).toInt()
                  else qtyWord.toIntOrNull() ?: return "Usage: !trade buy <TICKER> <QTY|max>"
        if (qty < 1) return "Not enough cash for even one share (\$${"%,.2f".format(s.price)})."
        val cost = s.price * qty
        if (cost > cash) return "Not enough cash. Need \$${"%,.2f".format(cost)}, have \$${"%,.2f".format(cash)}"
        val prevQty = holdings.getOrDefault(t, 0)
        val prevAvg = avgCost.getOrDefault(t, 0.0)
        val newQty = prevQty + qty
        avgCost[t] = (prevQty * prevAvg + qty * s.price) / newQty
        holdings[t] = newQty
        cash = round2(cash - cost)
        save()
        return "Bought $qty $t @ \$${"%,.2f".format(s.price)} = \$${"%,.2f".format(cost)}\nCash left: \$${"%,.2f".format(cash)}"
    }

    fun sell(ticker: String, qtyWord: String): String {
        val t = ticker.uppercase()
        val s = market[t] ?: return "No such ticker: $t"
        val have = holdings.getOrDefault(t, 0)
        val qty = if (qtyWord.equals("all", true)) have
                  else qtyWord.toIntOrNull() ?: return "Usage: !trade sell <TICKER> <QTY|all>"
        if (qty < 1 || qty > have) return "You only hold $have shares of $t"
        val proceeds = s.price * qty
        val avg = avgCost.getOrDefault(t, s.price)
        realizedPnl = round2(realizedPnl + (s.price - avg) * qty)
        holdings[t] = have - qty
        if (holdings[t] == 0) { holdings.remove(t); avgCost.remove(t) }
        cash = round2(cash + proceeds)
        save()
        return "Sold $qty $t @ \$${"%,.2f".format(s.price)} = \$${"%,.2f".format(proceeds)}  (P/L ${if ((s.price-avg)*qty>=0) "+" else "-"}\$${"%,.2f".format(Math.abs((s.price-avg)*qty))})\nCash: \$${"%,.2f".format(cash)}"
    }

    fun reset(): String {
        cash = startingCash
        holdings.clear(); avgCost.clear()
        day = 1; realizedPnl = 0.0; lastNews = emptyList()
        netWorthHistory.clear(); netWorthHistory.add(startingCash)
        market = startingMarket()
        save()
        return "Fresh start — \$${startingCash.toInt()} cash, day 1."
    }

    private fun save() {
        try {
            val obj = JSONObject()
            obj.put("cash", cash); obj.put("day", day); obj.put("realizedPnl", realizedPnl)
            val hJ = JSONObject(); for ((t, q) in holdings) hJ.put(t, q)
            obj.put("holdings", hJ)
            val aJ = JSONObject(); for ((t, a) in avgCost) aJ.put(t, a)
            obj.put("avgCost", aJ)
            val nJ = JSONArray(); for (v in netWorthHistory) nJ.put(v)
            obj.put("netWorthHistory", nJ)
            val mJ = JSONObject()
            for ((t, s) in market) {
                val sj = JSONObject()
                sj.put("price", s.price)
                sj.put("history", JSONArray(s.history))
                mJ.put(t, sj)
            }
            obj.put("market", mJ)
            file.parentFile?.mkdirs()
            file.writeText(obj.toString())
        } catch (_: Exception) {
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val obj = JSONObject(file.readText())
            cash = obj.optDouble("cash", startingCash)
            day = obj.optInt("day", 1)
            realizedPnl = obj.optDouble("realizedPnl", 0.0)
            obj.optJSONObject("holdings")?.let { h ->
                val keys = h.keys(); while (keys.hasNext()) { val k = keys.next(); holdings[k] = h.getInt(k) }
            }
            obj.optJSONObject("avgCost")?.let { a ->
                val keys = a.keys(); while (keys.hasNext()) { val k = keys.next(); avgCost[k] = a.getDouble(k) }
            }
            obj.optJSONArray("netWorthHistory")?.let { arr ->
                netWorthHistory.clear()
                for (i in 0 until arr.length()) netWorthHistory.add(arr.getDouble(i))
            }
            obj.optJSONObject("market")?.let { m ->
                val keys = m.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val sj = m.getJSONObject(k)
                    market[k]?.let { s ->
                        s.price = sj.getDouble("price")
                        s.history.clear()
                        val arr = sj.getJSONArray("history")
                        for (i in 0 until arr.length()) s.history.add(arr.getDouble(i))
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}
