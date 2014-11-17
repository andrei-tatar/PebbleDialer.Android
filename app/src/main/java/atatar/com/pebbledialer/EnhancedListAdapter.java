package atatar.com.pebbledialer;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
* Created by X550L-User1 on 08-Nov-14.
*/
class EnhancedListAdapter extends BaseAdapter {

    private final LayoutInflater mLayoutInflater;
    private List<Contact > mItems = new ArrayList<Contact >();

    public EnhancedListAdapter(LayoutInflater layoutInflater)
    {
        mLayoutInflater = layoutInflater;
    }

    public void remove(int position) {
        mItems.remove(position);
        notifyDataSetChanged();
    }

    public void insert(int position, Contact  item) {
        mItems.add(position, item);
        notifyDataSetChanged();
    }

    public void add(Contact  item){
        mItems.add(item);
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    /**
     * How many items are in the data set represented by this Adapter.
     *
     * @return Count of items.
     */
    @Override
    public int getCount() {
        return mItems.size();
    }

    /**
     * Get the data item associated with the specified position in the data set.
     *
     * @param position Position of the item whose data we want within the adapter's
     *                 data set.
     * @return The data at the specified position.
     */
    @Override
    public Object getItem(int position) {
        return mItems.get(position);
    }

    /**
     * Get the row id associated with the specified position in the list.
     *
     * @param position The position of the item within the adapter's data set whose row id we want.
     * @return The id of the item at the specified position.
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Get a View that displays the data at the specified position in the data set. You can either
     * create a View manually or inflate it from an XML layout file. When the View is inflated, the
     * parent View (GridView, ListView...) will apply default layout parameters unless you use
     * {@link android.view.LayoutInflater#inflate(int, android.view.ViewGroup, boolean)}
     * to specify a root view and to prevent attachment to the root.
     *
     * @param position    The position of the item within the adapter's data set of the item whose view
     *                    we want.
     * @param convertView The old view to reuse, if possible. Note: You should check that this view
     *                    is non-null and of an appropriate type before using. If it is not possible to convert
     *                    this view to display the correct data, this method can create a new view.
     *                    Heterogeneous lists can specify their number of view types, so that this View is
     *                    always of the right type (see {@link #getViewTypeCount()} and
     *                    {@link #getItemViewType(int)}).
     * @param parent      The parent that this view will eventually be attached to
     * @return A View corresponding to the data at the specified position.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder holder;
        if(convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.list_item, parent, false);
            // Clicking the delete icon, will read the position of the item stored in
            // the tag and delete it from the list. So we don't need to generate a new
            // onClickListener every time the content of this view changes.
            final View origView = convertView;
            /*convertView.findViewById(R.id.action_delete).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListView.delete(((ViewHolder)origView.getTag()).position);
                }
            });*/

            holder = new ViewHolder();
            assert convertView != null;
            holder.nameTextView = (TextView) convertView.findViewById(R.id.nameTextView);
            holder.numberTextView = (TextView)convertView.findViewById(R.id.numberTextView);
            holder.imageView = (ImageView) convertView.findViewById(R.id.contactImage);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.position = position;

        Contact  contact = mItems.get(position);
        if (contact.ImageUri != null)
            holder.imageView.setImageURI(Uri.parse(contact.ImageUri));
        holder.numberTextView.setText(contact.Phone.Label + " " + contact.Phone.Number);
        holder.nameTextView.setText(contact.Name);

        return convertView;
    }

    private class ViewHolder {
        ImageView imageView;
        TextView nameTextView, numberTextView;
        int position;
    }

}
