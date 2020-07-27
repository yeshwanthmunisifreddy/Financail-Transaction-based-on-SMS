package technology.nine.payo.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import io.realm.RealmList;
import technology.nine.payo.R;
import technology.nine.payo.model.Sms;

import static technology.nine.payo.utils.helper.getDateTimeString;

public class AllSmsAdapter extends RecyclerView.Adapter<AllSmsAdapter.SmsAdapterHolder> {

    Context context;
    RealmList<Sms> smsListArrayList = new RealmList<>();
    private TagAddListener listener;

    public interface TagAddListener {
        void onClick(String id);
    }

    public AllSmsAdapter(Context context, RealmList<Sms> smsListArrayList,TagAddListener listener) {
        this.context = context;
        this.smsListArrayList = smsListArrayList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SmsAdapterHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SmsAdapterHolder(LayoutInflater.from(parent.getContext()).
                inflate(R.layout.item_sms_layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull SmsAdapterHolder holder, int position) {
        Sms smsList = smsListArrayList.get(position);
        if (smsList.getType().equals("credited")) {
            holder.status.setText("Credited");
            holder.status.setTextColor(context.getResources().getColor(R.color.green));
        } else {
            holder.status.setText("Debited");
            holder.status.setTextColor(context.getResources().getColor(R.color.red));
        }

        holder.amount.setText("\u20b9" + smsList.getAmount());
        holder.date.setText(getDateTimeString(smsList.getDate()));
        holder.icon_action.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.icon_action);

            popup.getMenu().add(Menu.NONE, 1, 1, "Add Tag");
            popup.show();

            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    int i = item.getItemId();
                    popup.dismiss();
                    listener.onClick(smsListArrayList.get(position).getId());
                    return false;
                }
            });
            popup.show();
        });

    }

    @Override
    public int getItemCount() {
        return smsListArrayList.size();
    }

    public static class SmsAdapterHolder extends RecyclerView.ViewHolder {
        TextView status;
        TextView amount;
        TextView date;
        ImageView icon_action;

        public SmsAdapterHolder(@NonNull View itemView) {
            super(itemView);
            status = itemView.findViewById(R.id.status);
            amount = itemView.findViewById(R.id.amount);
            date = itemView.findViewById(R.id.date);
            icon_action = itemView.findViewById(R.id.icon_action);
        }
    }


}

