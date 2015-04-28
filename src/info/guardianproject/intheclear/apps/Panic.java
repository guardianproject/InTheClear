
package info.guardianproject.intheclear.apps;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import info.guardianproject.intheclear.ITCConstants;
import info.guardianproject.intheclear.ITCPreferences;
import info.guardianproject.intheclear.R;
import info.guardianproject.intheclear.controllers.PanicController;
import info.guardianproject.intheclear.controllers.ShoutController;
import info.guardianproject.intheclear.data.PhoneInfo;
import info.guardianproject.utils.EndActivity;

public class Panic extends Activity implements OnClickListener, OnDismissListener {

    SharedPreferences _sp;
    boolean oneTouchPanic;

    TextView shoutReadout, panicProgress, countdownReadout;
    ListView wipeDisplayList;
    Button controlPanic, cancelCountdown, panicControl;

    Intent panic, toKill;
    int panicState = ITCConstants.PanicState.AT_REST;

    Dialog countdown;
    CountDownTimer cd;

    ProgressDialog panicStatus;
    String currentPanicStatus;

    private ResultReceiver resultReceiver = new ResultReceiver(new Handler()) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            switch (resultCode) {

// TODO wipeDisplayList.setAdapter(new WipeDisplayAdaptor(Panic.this, pc.returnWipeSettings()));

                case PanicController.PROGRESS:
                    updateProgressWindow(resultData.getString(PanicController.KEY_PROGRESS_MESSAGE));
                    break;
            }
        }
    };

    private BroadcastReceiver killReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            killActivity();
        }

    };
    IntentFilter killFilter = new IntentFilter();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.panic);

        _sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        panicControl = (Button) findViewById(R.id.panicControl);
        shoutReadout = (TextView) findViewById(R.id.shoutReadout);

        // if this is not a cell phone, then no need to show the panic message
        if (TextUtils.isEmpty(PhoneInfo.getIMEI())) {
            shoutReadout.setVisibility(View.GONE);
            TextView shoutReadoutTitle = (TextView) findViewById(R.id.shoutReadoutTitle);
            shoutReadoutTitle.setVisibility(View.GONE);
        } else {
            String panicMsg = _sp.getString(ITCConstants.Preference.DEFAULT_PANIC_MSG, "");
            shoutReadout.setText("\n\n" + panicMsg + "\n\n"
                    + ShoutController.buildShoutData(getResources()));
        }

        wipeDisplayList = (ListView) findViewById(R.id.wipeDisplayList);

        panicStatus = new ProgressDialog(this);
        panicStatus.setButton(
                getResources().getString(R.string.KEY_PANIC_MENU_CANCEL),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        cancelPanic();
                    }
                }
                );
        panicStatus.setMessage(currentPanicStatus);
        panicStatus.setTitle(getResources().getString(R.string.KEY_PANIC_BTN_PANIC));

    }

    @Override
    public void onResume() {
        killFilter.addAction(this.getClass().toString());
        registerReceiver(killReceiver, killFilter);
        super.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        alignPreferences();
        if (!oneTouchPanic) {
            panicControl.setText(this.getResources().getString(R.string.KEY_PANIC_BTN_PANIC));
            panicControl.setOnClickListener(this);
        } else {
            panicControl.setText(getString(R.string.KEY_PANIC_MENU_CANCEL));
            panicControl.setOnClickListener(this);
            doPanic();
        }
    }

    @Override
    public void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);

        if (i.hasExtra("ReturnFrom") && i.getIntExtra("ReturnFrom", 0) == ITCConstants.Panic.RETURN) {
            // the app is being launched from the notification tray.

        }

        if (i.hasExtra("PanicCount"))
            Log.d(ITCConstants.Log.ITC, "Panic Count at: " + i.getIntExtra("PanicCount", 0));
    }

    @Override
    public void onPause() {
        unregisterReceiver(killReceiver);
        super.onPause();
    }

    private void alignPreferences() {
        oneTouchPanic = false;
        String recipients = _sp.getString(ITCConstants.Preference.CONFIGURED_FRIENDS, "");
        if (recipients.compareTo("") == 0) {
            AlertDialog.Builder d = new AlertDialog.Builder(this);
            d.setMessage(getResources().getString(R.string.KEY_SHOUT_PREFSFAIL))
                    .setCancelable(false)
                    .setPositiveButton(getResources().getString(R.string.KEY_OK),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    Panic.this.launchPreferences();
                                }
                            });
            AlertDialog a = d.create();
            a.show();
        } else {
            oneTouchPanic = _sp.getBoolean(ITCConstants.Preference.DEFAULT_ONE_TOUCH_PANIC, false);
        }
    }

    public void cancelPanic() {
        if (panicState == ITCConstants.PanicState.IN_COUNTDOWN) {
            // if panic hasn't started, then just kill the countdown
            cd.cancel();
        }

        toKill = new Intent(this, EndActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        finish();
        startActivity(toKill);

    }

    @Override
    public void onClick(View v) {
        if (v == panicControl && panicState == ITCConstants.PanicState.AT_REST) {
            doPanic();
        } else if (v == panicControl && panicState != ITCConstants.PanicState.AT_REST) {
            cancelPanic();
        }

    }

    @Override
    public void onDismiss(DialogInterface d) {

    }

    public void updateProgressWindow(String message) {
        panicStatus.setMessage(message);
    }

    public void killActivity() {
        Intent toKill = new Intent(Panic.this, EndActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(toKill);
    }

    public void launchPreferences() {
        Intent toPrefs = new Intent(this, ITCPreferences.class);
        startActivity(toPrefs);
    }

    private void doPanic() {
        panicState = ITCConstants.PanicState.IN_COUNTDOWN;
        panicControl.setText(getString(R.string.KEY_PANIC_MENU_CANCEL));
        cd = new CountDownTimer(ITCConstants.Duriation.COUNTDOWN,
                ITCConstants.Duriation.COUNTDOWNINTERVAL) {
            int t = 5;

            @Override
            public void onFinish() {
                // start the panic
                startService(new Intent(getApplicationContext(), PanicController.class));

                // kill the activity
                killActivity();
            }

            @Override
            public void onTick(long millisUntilFinished) {
                panicStatus.setMessage(
                        getString(R.string.KEY_PANIC_COUNTDOWNMSG) +
                                " " + t + " " +
                                getString(R.string.KEY_SECONDS)
                        );
                t--;
            }

        };

        panicStatus.show();
        cd.start();

    }
}
