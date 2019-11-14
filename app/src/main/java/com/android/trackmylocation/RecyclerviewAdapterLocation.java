package com.android.trackmylocation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;


public class RecyclerviewAdapterLocation extends RecyclerView.Adapter<RecyclerviewAdapterLocation.MyViewHolder> {

    List<LocationEntity> arrayList;
    Context context;
    int viewtype;

    public RecyclerviewAdapterLocation(){

    }
    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
       return new MyViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.location_item,parent,false));
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        LocationEntity locationEntity=arrayList.get(position);
        holder.address.setText(locationEntity.getAddress());
        holder.time_txt.setText(locationEntity.getDate_time());
        holder.location_details.setText(String.format("%s,%s:%s", locationEntity.getLatitude(), locationEntity.getLongitude(), locationEntity.getAccuracy()));

    }

    public void updateList( List<LocationEntity> arrayList){
        this.arrayList=arrayList;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return arrayList!=null?arrayList.size():0;
    }

    class MyViewHolder extends RecyclerView.ViewHolder{
        TextView address;
        TextView time_txt;
        TextView location_details;

        MyViewHolder(View itemView) {
            super(itemView);
            address=itemView.findViewById(R.id.address);
            time_txt=itemView.findViewById(R.id.time_txt);
            location_details=itemView.findViewById(R.id.location_details);
        }
    }
}
