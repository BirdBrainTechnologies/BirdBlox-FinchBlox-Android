package com.birdbraintechnologies.birdblox.Dialogs;

import android.app.AlertDialog;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.birdbraintechnologies.birdblox.MainWebView;
import com.birdbraintechnologies.birdblox.R;

import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;


/**
 * Class for showing a progress dialog - such as a dialog shown when a file is
 * downloading or uploading.
 *
 * @author krissie
 */

public class ProgressDialog {
    private final String TAG = this.getClass().getSimpleName();

    private AsyncTask task;

    private AlertDialog.Builder builder;
    private AlertDialog dialog;
    public ProgressBar progressBar;
    private Button cancelButton;
    private TextView showText;

    private boolean progressIsDeterminate;
    private int dialogViewLayout;
    private int progressBarView;
    private int cancelBtnView;
    private int showTextView;

    public ProgressDialog(AsyncTask t, boolean isDeterminate) {
        task = t;
        progressIsDeterminate = isDeterminate;

        //Set the layout to be used. If we will show progress with an updated progress bar,
        // the process is determinate.
        if (progressIsDeterminate){
            dialogViewLayout = R.layout.progress_determinate;
            progressBarView = R.id.determinate_pb;
            cancelBtnView = R.id.determinate_btn;
            showTextView = R.id.determinate_tv;
        } else {
            dialogViewLayout = R.layout.progress_indeterminate;
            progressBarView = R.id.indeterminate_pb;
            cancelBtnView = R.id.indeterminate_btn;
            showTextView = R.id.indeterminate_tv;
        }

        builder = new AlertDialog.Builder(mainWebViewContext);
    }

    public void show() {
        new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                builder.setCancelable(false);
                dialog = builder.create();

                final View dialogView = dialog.getLayoutInflater().inflate(dialogViewLayout, null);
                builder.setView(dialogView);

                progressBar = dialogView.findViewById(progressBarView);
                if (progressIsDeterminate) {
                    progressBar.setMax(100);
                    progressBar.setProgress(0);
                }
                progressBar.setVisibility(View.VISIBLE);

                cancelButton = dialogView.findViewById(cancelBtnView);
                cancelButton.setText(MainWebView.cancel_text);
                cancelButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        task.cancel(true);
                        dialog.cancel();
                    }
                });

                showText = dialogView.findViewById(showTextView);
                showText.setText(MainWebView.loading_text);
                dialog = builder.create();
                dialog.show();
            }
        });
    }

    public void close() {
        try {
            dialog.cancel();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Unable to close unzip dialog: " + e.getMessage());
        }
    }
}
