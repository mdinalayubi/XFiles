package it.pgp.xfiles.service.visualization;

import android.app.Service;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import it.pgp.xfiles.R;
import it.pgp.xfiles.utils.Pair;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

/**
 * Created by pgp on 10/07/17
 * Overlay with two progress bars (for showing file number and size progress)
 */

public class MovingRibbonTwoBars extends ProgressIndicator implements View.OnTouchListener {

    public ProgressBar pbOuter;
    public ProgressBar pbInner; // outer progress: current number of files, inner: current size
    public TextView pbSpeed;

    public long lastProgressTime = 0;
    public Pair<Integer,Integer> lastOuterProgress;

    private float offsetX;
    private float offsetY;
    private int originalXPos;
    private int originalYPos;
    private boolean moving;

    public MovingRibbonTwoBars(final Service service, final WindowManager wm) {
        this.wm = wm;

        LayoutInflater inflater = (LayoutInflater) service.getSystemService(LAYOUT_INFLATER_SERVICE);
        oView = (LinearLayout) inflater.inflate(R.layout.ribbon_two, null);

        pbOuter = oView.findViewById(R.id.pbOuter);
        pbInner = oView.findViewById(R.id.pbInner);
        pbSpeed = oView.findViewById(R.id.pbSpeed);
        lastProgressTime = System.currentTimeMillis();

        pbOuter.setMax(100);
        pbOuter.setIndeterminate(false);
        pbOuter.setBackgroundColor(0x880000ff);

        pbInner.setMax(100);
        pbInner.setIndeterminate(false);
        pbInner.setBackgroundColor(0x8800ff00);

        oView.setOnTouchListener(this);

//        wm.addView(oView, ViewType.CONTAINER.getParams());
        addViewToOverlay(oView, ViewType.CONTAINER.getParams());

        topLeftView = new View(service);

//        wm.addView(topLeftView,ViewType.ANCHOR.getParams());
        addViewToOverlay(topLeftView,ViewType.ANCHOR.getParams());
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getRawX();
            float y = event.getRawY();

            moving = false;

            int[] location = new int[2];
            v.getLocationOnScreen(location);

            originalXPos = location[0];
            originalYPos = location[1];

            offsetX = originalXPos - x;
            offsetY = originalYPos - y;

        }
        else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            int[] topLeftLocationOnScreen = new int[2];
            topLeftView.getLocationOnScreen(topLeftLocationOnScreen);

//            Log.i("onTouch","topLeftY="+topLeftLocationOnScreen[1]);
//            Log.i("onTouch","originalY="+originalYPos);

            float x = event.getRawX();
            float y = event.getRawY();

            WindowManager.LayoutParams params = (WindowManager.LayoutParams) v.getLayoutParams();

            int newX = (int) (offsetX + x);
            int newY = (int) (offsetY + y);

            if (Math.abs(newX - originalXPos) < 1 && Math.abs(newY - originalYPos) < 1 && !moving) {
                return false;
            }

            params.x = newX - (topLeftLocationOnScreen[0]);
            params.y = newY - (topLeftLocationOnScreen[1]);

            wm.updateViewLayout(v, params);
            moving = true;
        }
        else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (moving) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void setProgress(Pair<Integer,Integer>... values) {
        pbOuter.setProgress((int) Math.round(values[0].i * 100.0 / values[0].j));
        pbInner.setProgress((int) Math.round(values[1].i * 100.0 / values[1].j));
        if(lastOuterProgress == null) {
            lastProgressTime = System.currentTimeMillis();
            lastOuterProgress = values[0];
            pbSpeed.setText("0 Mbps");
        }
        else {
            long dt = lastProgressTime;
            lastProgressTime = System.currentTimeMillis();
            dt = lastProgressTime - dt;

            long ds = lastOuterProgress.i;
            lastOuterProgress = values[0];
            ds = lastOuterProgress.i - ds;

            double speedMbps = ds/(dt*1000.0);
            pbSpeed.setText(String.format("%.2f Mbps",speedMbps));
        }
    }
}
