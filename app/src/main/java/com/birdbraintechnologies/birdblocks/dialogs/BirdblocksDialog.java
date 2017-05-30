package com.birdbraintechnologies.birdblocks.dialogs;

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

import com.birdbraintechnologies.birdblocks.httpservice.requesthandlers.HostDeviceHandler;

/**
 * Created by tsun on 2/21/17.
 */

public class BirdblocksDialog extends DialogFragment {

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
            final EditText input = new EditText(getActivity());
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
            Log.d("Input", "hi");
            input.setHint(hint);
            builder.setView(input)
                    .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            sendResponseBroadcast("\'" + input.getText().toString() + "\'");
                        }
                    });
        } else if (type == DialogType.CHOICE) {
            // Build choice dialog
            String button1Text = getArguments().getString("button1");
            String button2Text = getArguments().getString("button2");
            builder.setNegativeButton(button1Text, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    sendResponseBroadcast("1");
                }
            }).setPositiveButton(button2Text, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    sendResponseBroadcast("2");
                }
            });
            // builder.setCancelable(true);
            // builder.setCanceledOnTouchOutside(true);
        }

        return builder.create();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        sendResponseBroadcast("Cancelled");
    }

    private void sendResponseBroadcast(String response) {
        Intent responseIntent = new Intent(HostDeviceHandler.DIALOG_RESPONSE);
        responseIntent.putExtra("response", response);
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(responseIntent);
    }


    public enum DialogType {
        INPUT, CHOICE;

        public static DialogType fromString(String s) {
            switch (s) {
                case "input":
                    return INPUT;
                case "choice":
                    return CHOICE;
                default:
                    return null;
            }
        }

        @Override
        public String toString() {
            switch (this) {
                case INPUT:
                    return "input";
                case CHOICE:
                    return "choice";
                default:
                    return "";
            }
        }
    }
}
