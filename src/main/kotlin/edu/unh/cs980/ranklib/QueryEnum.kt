@file:JvmName("KotQueryEnum")
package edu.unh.cs980.ranklib

enum class QueryEnum(val value: String) : CharSequence by value {
    AVERAGE_ABSTRACT("average_abstract"),
    HYPERLINK("hyperlink"),
    ABSTRACT_SDM("abstract_sdm"),
    SDM("sdm"),
    NAT_SDM("nat_sdm"),
    SDM_SECTION("sdm_section"),
    SDM_EXPANSION("sdm_expansion"),
    TFIDF_SECTION("tfidf_section"),
    STRING_SIMILARITY_SECTION("string_similarity_section"),
    COMBINED("combined"),
    SUPER_AWESOME_TEAMWORK("super_awesome_teamwork"),
    ;

    override fun toString() = value

    companion object {
        private val map: Map<String, QueryEnum> = QueryEnum.values().associateBy(QueryEnum::value)
        fun fromString(string: String) = map[string]

        fun getCommands() = QueryEnum.values()
            .map(QueryEnum::value)
            .sorted()
    }
}
