package com.example.opggyumi.alarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.opggyumi.R
import com.example.opggyumi.model.NotificationItem
import com.example.opggyumi.comment.getRelativeTime

class NotificationAdapter(
    private val items: List<NotificationItem>
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvNotificationTitle)
        val time: TextView = v.findViewById(R.id.tvNotificationTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
        val n = items[pos]
        val verb = if (n.type == "reply") "답글을" else "댓글을"
        holder.title.text = "● ${n.senderNickname} 님이 $verb 달았습니다"
        holder.time.text = getRelativeTime(n.timestamp)
        // (여기에 클릭 이벤트: 상세 페이지로 이동하도록 Intent 연결 가능)
    }
}