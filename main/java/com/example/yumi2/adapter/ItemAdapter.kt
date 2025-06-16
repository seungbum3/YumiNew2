package com.example.yumi2.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.yumi2.R
import com.example.yumi2.model.Item

class ItemAdapter(
    private val originalItemList: List<Item>,
    private val itemClickListener: (Item) -> Unit
) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>(), Filterable {

    // 필터 적용 후의 리스트
    private var filteredItemList: List<Item> = originalItemList

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemImage: ImageView = view.findViewById(R.id.itemImage)
        val itemName: TextView = view.findViewById(R.id.itemName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_list, parent, false)

        // 부모 RecyclerView의 너비를 가져와서 아이템 크기 조정
        val parentWidth = parent.width
        val itemWidth = (parentWidth / 5) - 10  // 5개 아이템 배치, 간격 확보 (여백 포함)

        val layoutParams = view.layoutParams
        layoutParams.width = itemWidth
        layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT  // 높이 자동 조정
        view.layoutParams = layoutParams

        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = filteredItemList[position]
        holder.itemName.text = item.name

        Glide.with(holder.itemView.context)
            .load(item.imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.error_image)
            .into(holder.itemImage)

        holder.itemView.setBackgroundResource(R.drawable.item_slot_background)
        holder.itemView.setOnClickListener { itemClickListener(item) }
    }

    override fun getItemCount(): Int = filteredItemList.size

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.lowercase()?.trim() ?: ""
                val filtered = when {
                    query.isEmpty() -> originalItemList
                    query == "*" -> emptyList()
                    else -> originalItemList.filter { item ->
                        val name = item.name.lowercase()
                        val rawDesc = item.description.lowercase()
                        val description = if (rawDesc == "설명 없음") "" else rawDesc.replace("*", "")
                        val effect = item.effect.lowercase()
                        val stats = item.stats.lowercase()

                        name.contains(query) ||
                                description.contains(query) ||
                                effect.contains(query) ||
                                stats.contains(query)
                    }
                }
                return FilterResults().apply { values = filtered }
            }


            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredItemList = results?.values as List<Item>
                notifyDataSetChanged()
            }
        }
    }


    fun getItemList(): List<Item> {
        return originalItemList
    }


    fun filterByMultipleCategories(categories: Set<String>) {
        Log.d("FILTER", "필터링 시작 - 적용된 필터: $categories")

        val filterMapping = mapOf(
            "공격력" to listOf("Damage"),
            "치명타 확률" to listOf("CriticalStrike"),
            "공격 속도" to listOf("AttackSpeed"),
            "적중 시 효과" to listOf("OnHit"),
            "물리 관통력" to listOf("ArmorPenetration", "Lethality"),
            "주문력" to listOf("SpellDamage"),
            "마나 및 재생" to listOf("Mana", "ManaRegen"),
            "마법 관통력" to listOf("MagicPenetration"),
            "체력 및 재생" to listOf("Health", "HealthRegen"),
            "방어력" to listOf("Armor"),
            "마법 저항력" to listOf("SpellBlock"),
            "스킬 가속" to listOf("CooldownReduction", "AbilityHaste"),
            "이동 속도" to listOf("Boots", "NonbootsMovement", "MovementSpeed", "SlowResist"),
            "생명력 흡수 및 흡혈" to listOf("LifeSteal", "SpellVamp", "Omnivamp")
        )

        // 선택된 각 필터별로, 해당하는 태그 중 하나라도 item에 있으면 만족하는 것으로 처리
        filteredItemList = originalItemList.filter { item ->
            val itemTags = item.tags ?: emptyList()
            // 모든 선택된 필터에 대해, 해당 필터에 대응하는 태그 중 하나가 itemTags에 있어야 함
            categories.all { filter ->
                val mapped = filterMapping[filter] ?: emptyList()
                itemTags.any { it in mapped }
            }
        }

        Log.d("FILTER", "필터링 완료 - 남은 아이템 수: ${filteredItemList.size}")
        notifyDataSetChanged()
    }








    // 필터 초기화(전체 아이템 보기)
    fun resetFilters() {
        filteredItemList = originalItemList
        notifyDataSetChanged()
    }
}
