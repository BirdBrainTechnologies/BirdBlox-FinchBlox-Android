package com.birdbraintechnologies.birdblox.Dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;

import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.HostDeviceHandler;

import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;

/**
 * Created by tsun on 2/21/17.
 *
 * @author Terence Sun (tsun1215)
 * @author Shreyan Bakshi (AppyFizz)
 */

public class BirdBloxDialog extends DialogFragment {

    public static DialogType lastOpened;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // TODO: Extract strings
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        DialogType type = DialogType.fromString(getArguments().getString("type"));
        String title = getArguments().getString("title");
        String msg = getArguments().getString("message");

        builder.setTitle(title).setMessage(msg);

        if (type == DialogType.INPUT) {
            // Build input dialog
            String hint = getArguments().getString("hint");
            String defaultText = getArguments().getString("default");
            boolean select = getArguments().getBoolean("select");
            final EditText input = new EditText(getActivity());
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
            Log.d("Properties3", hint);
            input.setHint(hint);
            input.setText(defaultText);
            if (select) input.setSelectAllOnFocus(true);
            lastOpened = DialogType.INPUT;
            builder.setView(input)
                    .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            sendResponseBroadcast("'" + input.getText().toString() + "'");
                            runJavascript("CallbackManager.dialog.promptResponded(false, '" + input.getText().toString() + "')");
                        }
                    });
        } else if (type == DialogType.CHOICE) {
            // Build choice dialog
            String button1Text = getArguments().getString("button1");
            String button2Text = getArguments().getString("button2");
            lastOpened = DialogType.CHOICE;
            builder.setNegativeButton(button1Text, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    sendResponseBroadcast("1");
                    runJavascript("CallbackManager.dialog.choiceResponded(false, true)");
                }
            }).setPositiveButton(button2Text, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    sendResponseBroadcast("2");
                    runJavascript("CallbackManager.dialog.choiceResponded(false, false)");
                }
            });
        }
        return builder.create();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        sendResponseBroadcast("Cancelled");
        if (lastOpened == DialogType.INPUT) {
            runJavascript("CallbackManager.dialog.promptResponded(true)");
        } else if (lastOpened == DialogType.CHOICE) {
            runJavascript("CallbackManager.dialog.choiceResponded(true)");
        }
        super.onCancel(dialog);
    }

    private void sendResponseBroadcast(String response) {
        Intent responseIntent = new Intent(HostDeviceHandler.DIALOG_RESPONSE);
        responseIntent.putExtra("response", response);
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(responseIntent);
    }
}
