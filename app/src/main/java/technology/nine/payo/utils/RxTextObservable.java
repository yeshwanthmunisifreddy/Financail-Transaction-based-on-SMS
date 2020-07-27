package technology.nine.payo.utils;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;



import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;

public class RxTextObservable {
    public static Observable<String> fromSearch(SearchView searchView) {
        final PublishSubject<String> subject = PublishSubject.create();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                subject.onNext(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                subject.onNext(newText);
                return false;
            }
        });
        return subject;
    }

    public static Observable<String> fromTextView(TextView textView) {
        final BehaviorSubject<String> behavior = BehaviorSubject.create();
        textView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                behavior.onNext(textView.getText().toString().trim());
            }
        });
        return behavior;
    }

}
