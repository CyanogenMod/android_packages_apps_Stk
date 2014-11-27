/*
 * Copyright (c) 2009,2012-2014 The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.stk;

import com.android.internal.telephony.cat.CatLog;
import com.android.internal.telephony.cat.TextMessage;
import com.android.internal.telephony.cat.CatLog;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

/**
 * AlretDialog used for DISPLAY TEXT commands.
 *
 */
public class StkDialogActivity extends Activity implements View.OnClickListener {
    // members
    TextMessage mTextMsg;
    private boolean mIsResponseSent = false;

    StkAppService appService = StkAppService.getInstance();
    private int mSlotId = 0;

    Handler mTimeoutHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
            case MSG_ID_TIMEOUT:
                sendResponse(StkAppService.RES_ID_TIMEOUT);
                finish();
                break;
            }
        }
    };

    //keys) for saving the state of the dialog in the icicle
    private static final String TEXT = "text";

    // message id for time out
    private static final int MSG_ID_TIMEOUT = 1;

    // buttons id
    public static final int OK_BUTTON = R.id.button_ok;
    public static final int CANCEL_BUTTON = R.id.button_cancel;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        requestWindowFeature(Window.FEATURE_LEFT_ICON);

        setContentView(R.layout.stk_msg_dialog);

        Button okButton = (Button) findViewById(R.id.button_ok);
        Button cancelButton = (Button) findViewById(R.id.button_cancel);

        okButton.setOnClickListener(this);
        cancelButton.setOnClickListener(this);

    }

    public void onClick(View v) {
        String input = null;
        switch (v.getId()) {
            case OK_BUTTON:
                cancelTimeOut();
                sendResponse(StkAppService.RES_ID_CONFIRM, true);
                break;
            case CANCEL_BUTTON:
                cancelTimeOut();
                sendResponse(StkAppService.RES_ID_CONFIRM, false);
                break;
        }
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
            cancelTimeOut();
            sendResponse(StkAppService.RES_ID_BACKWARD);
            finish();
            break;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        initFromIntent(getIntent());
        if (mTextMsg == null) {
            finish();
            return;
        }

        appService.setDisplayTextDlgVisibility(true, mSlotId);

        Window window = getWindow();

        TextView mMessageView = (TextView) window
                .findViewById(R.id.dialog_message);

        setTitle(mTextMsg.title);

        if (!(mTextMsg.iconSelfExplanatory && mTextMsg.icon != null)) {
            mMessageView.setText(mTextMsg.text);
        }

        if (mTextMsg.icon == null) {
            window.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,
                    R.drawable.ic_dialog_sim);
        } else {
            window.setFeatureDrawable(Window.FEATURE_LEFT_ICON,
                    new BitmapDrawable(mTextMsg.icon));
        }


        /*
         * If the userClear flag is set and dialogduration is set to 0, the display Text
         * should be displayed to user forever until some high priority event occurs
         * (incoming call, MMI code execution etc as mentioned under section
         * ETSI 102.223, 6.4.1)
         */
        if (StkApp.calculateDurationInMilis(mTextMsg.duration) == 0 &&
            !mTextMsg.responseNeeded && mTextMsg.userClear) {
            CatLog.d(this, "User should clear text..showing message forever");
            return;
        }
        startTimeOut(mTextMsg.userClear);
    }

    @Override
    public void onPause() {
        super.onPause();

        /*
         * do not cancel the timer here cancelTimeOut(). If any higher/lower
         * priority events such as incoming call, new sms, screen off intent,
         * notification alerts, user actions such as 'User moving to another activtiy'
         * etc.. occur during Display Text ongoing session,
         * this activity would receive 'onPause()' event resulting in
         * cancellation of the timer. As a result no terminal response is
         * sent to the card.
         */

        appService.setDisplayTextDlgVisibility(false, mSlotId);

    }

    @Override
    protected void onStart() {
        super.onStart();
        mIsResponseSent = false;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!mIsResponseSent) {
            sendResponse(StkAppService.RES_ID_TIMEOUT);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable(TEXT, mTextMsg);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mTextMsg = savedInstanceState.getParcelable(TEXT);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        CatLog.d(this, "onNewIntent - updating the same Dialog box");
        setIntent(intent);
    }

    private void sendResponse(int resId, boolean confirmed) {
        if (mTextMsg.responseNeeded) {
            Bundle args = new Bundle();
            args.putInt(StkAppService.OPCODE, StkAppService.OP_RESPONSE);
            args.putInt(StkAppService.RES_ID, resId);
            args.putBoolean(StkAppService.CONFIRMATION, confirmed);
			args.putInt(StkAppService.SLOT_ID, mSlotId);
            startService(new Intent(this, StkAppService.class).putExtras(args));
            mIsResponseSent = true;
        }
    }

    private void sendResponse(int resId) {
        sendResponse(resId, true);
    }

    private void initFromIntent(Intent intent) {

        if (intent != null) {
            mTextMsg = intent.getParcelableExtra("TEXT");
            mSlotId = intent.getIntExtra(StkAppService.SLOT_ID, 0);
        } else {
            finish();
        }
    }

    private void cancelTimeOut() {
        mTimeoutHandler.removeMessages(MSG_ID_TIMEOUT);
    }

    private void startTimeOut(boolean waitForUserToClear) {

        // Reset timeout.
        cancelTimeOut();
        int dialogDuration = StkApp.calculateDurationInMilis(mTextMsg.duration);
        // If duration is specified, this has priority. If not, set timeout
        // according to condition given by the card.
        if (dialogDuration == 0) {
            if (waitForUserToClear) {
                dialogDuration = StkApp.DISP_TEXT_WAIT_FOR_USER_TIMEOUT;
            } else {
                dialogDuration = StkApp.DISP_TEXT_CLEAR_AFTER_DELAY_TIMEOUT;
            }
        }
        mTimeoutHandler.sendMessageDelayed(mTimeoutHandler
                .obtainMessage(MSG_ID_TIMEOUT), dialogDuration);
    }
}
