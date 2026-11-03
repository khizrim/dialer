package com.khizrim.dialer.callerid

/** Что мы знаем о незнакомом номере. Без строк: текст собирает [CallerId] из ресурсов. */
data class CallerFacts(
    val kind: Kind,
    val operator: String? = null,
    val region: String? = null,
    val countryIso: String? = null,
    val suspicion: Suspicion? = null,
) {
    enum class Kind { HIDDEN, SERVICE, MOBILE, LANDLINE, TOLL_FREE, VIRTUAL, FOREIGN, UNALLOCATED, UNKNOWN }

    /** Почему звонок похож на спам. null: не похож. */
    enum class Suspicion {
        /** Сеть сообщила, что номер не прошёл проверку (STIR/SHAKEN). */
        SPOOFED,
        /** Такого номера нет в реестре: его никому не выдавали, значит, подменён. */
        UNALLOCATED,
        /** Код страны, с которого обычно звонят ради обратного платного звонка. */
        RISKY_COUNTRY,
    }

    val suspicious: Boolean get() = suspicion != null
}

object Classifier {
    /**
     * Коды, с которых чаще всего идут «звонки-сбросы» (wangiri): перезвонишь, и спишут деньги.
     * Спутниковые и международные сети, малые островные и некоторые африканские страны.
     */
    val RISKY_CODES: Set<Int> = setOf(
        216, 222, 223, 224, 225, 227, 229, 231, 232, 235, 236, 237, 238, 239, 240, 241, 242, 243,
        245, 246, 247, 252, 253, 261, 269, 290,
        670, 672, 674, 675, 676, 677, 678, 680, 681, 682, 683, 685, 686, 687, 688, 689, 690, 691, 692,
        808, 870, 878, 881, 882, 883, 888, 979,
    )

    fun classify(number: CallerNumber, db: NumberDb?, verificationFailed: Boolean = false): CallerFacts {
        val facts = when (number) {
            CallerNumber.Hidden -> CallerFacts(CallerFacts.Kind.HIDDEN)
            is CallerNumber.Service -> CallerFacts(CallerFacts.Kind.SERVICE)
            CallerNumber.Unknown -> CallerFacts(CallerFacts.Kind.UNKNOWN)
            is CallerNumber.Kazakhstan -> CallerFacts(CallerFacts.Kind.FOREIGN, countryIso = "KZ")
            is CallerNumber.Foreign -> CallerFacts(
                CallerFacts.Kind.FOREIGN,
                countryIso = number.iso,
                suspicion = if (number.countryCode in RISKY_CODES) CallerFacts.Suspicion.RISKY_COUNTRY else null,
            )
            is CallerNumber.Russian -> russian(number, db)
        }
        return if (verificationFailed) facts.copy(suspicion = CallerFacts.Suspicion.SPOOFED) else facts
    }

    private fun russian(number: CallerNumber.Russian, db: NumberDb?): CallerFacts {
        if (number.code == 800) return CallerFacts(CallerFacts.Kind.TOLL_FREE)
        val mobile = number.code / 100 == 9
        val hit = db?.find(number.national) ?: return when {
            db == null -> CallerFacts(CallerFacts.Kind.UNKNOWN)
            // Мобильный реестр полный: невыданный номер почти наверняка подменён.
            mobile -> CallerFacts(CallerFacts.Kind.UNALLOCATED, suspicion = CallerFacts.Suspicion.UNALLOCATED)
            // В городских кодах реестр бывает неполным, пугать не будем.
            else -> CallerFacts(CallerFacts.Kind.LANDLINE)
        }
        val region = hit.region.takeIf { it.isNotBlank() && it != COUNTRY_WIDE }
        return when {
            hit.virtual -> CallerFacts(CallerFacts.Kind.VIRTUAL, hit.operator, region)
            hit.mobile -> CallerFacts(CallerFacts.Kind.MOBILE, hit.operator, region)
            else -> CallerFacts(CallerFacts.Kind.LANDLINE, hit.operator, region)
        }
    }

    /** Так в базе записаны диапазоны на всю страну. */
    const val COUNTRY_WIDE = "Россия"
}
