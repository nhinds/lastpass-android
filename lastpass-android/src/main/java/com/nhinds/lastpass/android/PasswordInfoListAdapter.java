package com.nhinds.lastpass.android;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.nhinds.lastpass.PasswordInfo;

public class PasswordInfoListAdapter extends ArrayAdapter<PasswordInfo> {

	private final Collection<? extends PasswordInfo> matchingPasswords;
	private final LayoutInflater layoutInflater;

	public PasswordInfoListAdapter(Context context, Collection<? extends PasswordInfo> allPasswords,
			Collection<? extends PasswordInfo> matchingPasswords) {
		super(context, 0, new ArrayList<PasswordInfo>(allPasswords));
		this.matchingPasswords = matchingPasswords;
		this.layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		sort(new BestMatchFirstComparator(matchingPasswords));
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final View view;
		if (convertView == null) {
			view = this.layoutInflater.inflate(R.layout.password_popup_text, parent, false);
		} else {
			view = convertView;
		}

		final PasswordInfo item = getItem(position);
		final TextView labelText = (TextView) view.findViewById(R.id.password_label);
		labelText.setText(item.getName());
		final TextView usernameText = (TextView) view.findViewById(R.id.password_username_label);
		usernameText.setText(item.getUsername());

		TextView heading = (TextView) view.findViewById(R.id.password_label_separator);
		if (position == 0 || position == this.matchingPasswords.size()) {
			heading.setVisibility(View.VISIBLE);
			if (this.matchingPasswords.isEmpty()) {
				heading.setText(R.string.all_passwords);
			} else if (position == 0) {
				heading.setText(R.string.matching_passwords);
			} else {
				heading.setText(R.string.other_passwords);
			}
		} else {
			heading.setVisibility(View.GONE);
		}
		return view;
	}

	private static class BestMatchFirstComparator implements Comparator<PasswordInfo> {
		private final Collection<? extends PasswordInfo> bestMatches;

		public BestMatchFirstComparator(Collection<? extends PasswordInfo> bestMatches) {
			this.bestMatches = bestMatches;
		}

		@Override
		public int compare(PasswordInfo lhs, PasswordInfo rhs) {
			boolean lhsMatches = this.bestMatches.contains(lhs);
			boolean rhsMatches = this.bestMatches.contains(rhs);
			if (lhsMatches != rhsMatches) {
				return lhsMatches ? -1 : 1;
			}
			return lhs.getName().compareToIgnoreCase(rhs.getName());
		}

	}
}
