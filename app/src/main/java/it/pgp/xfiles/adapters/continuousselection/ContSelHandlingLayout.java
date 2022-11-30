package it.pgp.xfiles.adapters.continuousselection;

import android.view.View;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import it.pgp.xfiles.MainActivity;
import it.pgp.xfiles.R;

public class ContSelHandlingLayout extends RelativeLayout {

    public final MainActivity mainActivity;
    public final OnTouchListener listener;

    public LinearLayout barLayout;
    public LinearLayout padLayout;

    // params to be used for listview
    static final LayoutParams lvParams = new RelativeLayout.LayoutParams(
            200,
            LayoutParams.MATCH_PARENT){{
       addRule(ALIGN_PARENT_END);
    }};

    static final LayoutParams gvParams = new RelativeLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
    );

    public final CSCheckboxes csCheckboxes; // to break cyclic dependency

    public boolean selStatus;
    public void toggleSelStatus(View v) {
        ((CheckedTextView)v).toggle();
        selStatus = !selStatus;
        padLayout.setVisibility(selStatus?VISIBLE:GONE);
        if(selStatus) barLayout.bringToFront();
        mainActivity.browserPager.switchNeutralArea();
    }

    public ContSelHandlingLayout(MainActivity context, OnTouchListener listener, CSCheckboxes csCheckboxes, boolean fullScreenPadLayout) {
        super(context);
        mainActivity = context;
        this.listener = listener;
        this.csCheckboxes = csCheckboxes;

        inflate(context,R.layout.cont_sel,this);

        csCheckboxes.setContinuousSelection(findViewById(R.id.toggleSelectMode));
        csCheckboxes.setInvertSelection(findViewById(R.id.invertSelection));
        csCheckboxes.setStickySelection(findViewById(R.id.stickySelection));

        barLayout = findViewById(R.id.barLayout);
        padLayout = findViewById(R.id.padLayout);
        if (!fullScreenPadLayout) {
            padLayout.setLayoutParams(lvParams);

        }
        padLayout.setOnTouchListener(listener);
        csCheckboxes.getContinuousSelection().setOnClickListener(this::toggleSelStatus);
    }
}
