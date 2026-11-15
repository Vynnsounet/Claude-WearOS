package com.medipote.app.ui.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.medipote.app.data.TrustedContact
import com.medipote.app.domain.Support

/** The dialer opens with the number ready: nothing is called until the user presses the call button there. */
fun dial(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(Support.cleanPhone(number)))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { Toast.makeText(context, "Impossible d'ouvrir le téléphone", Toast.LENGTH_SHORT).show() }
}

/** A message ready to send to someone of trust; the messaging app asks for confirmation. */
fun sendHelpMessage(context: Context, contact: TrustedContact) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(Support.cleanPhone(contact.phone))))
        .putExtra("sms_body", Support.ASK_FOR_HELP)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { Toast.makeText(context, "Impossible d'ouvrir les messages", Toast.LENGTH_SHORT).show() }
}
