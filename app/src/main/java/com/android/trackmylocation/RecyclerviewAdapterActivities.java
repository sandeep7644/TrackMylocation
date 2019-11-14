package com.android.trackmylocation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import androidx.recyclerview.widget.RecyclerView;


public class RecyclerviewAdapterActivities extends RecyclerView.Adapter<RecyclerviewAdapterActivities.MyViewHolder> {

    List<TransitionEntity> arrayList;
    Context context;
    int viewtype;
    ActivityClickListener activityClickListener;

    public RecyclerviewAdapterActivities(ActivityClickListener activityClickListener) {
        this.activityClickListener = activityClickListener;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new MyViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.transition_item, parent, false));
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        TransitionEntity transitionEntity = arrayList.get(position);
        holder.activity_txt.setText(transitionEntity.getActivity_type());
        holder.time_txt.setText(transitionEntity.getDate_time());

    }

    public void updateList(List<TransitionEntity> arrayList) {
        this.arrayList = arrayList;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return arrayList != null ? arrayList.size() : 0;
    }

    class MyViewHolder extends RecyclerView.ViewHolder {
        TextView activity_txt;
        TextView time_txt;

        MyViewHolder(View itemView) {
            super(itemView);
            activity_txt = itemView.findViewById(R.id.activity_txt);
            time_txt = itemView.findViewById(R.id.time_txt);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(activityClickListener!=null)
                    activityClickListener.onActivityItemClicked(arrayList.get(getAdapterPosition()));
                }
            });
        }
    }

    public interface ActivityClickListener {
        void onActivityItemClicked(TransitionEntity transitionEntity);
    }
}
