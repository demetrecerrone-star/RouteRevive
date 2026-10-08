package com.routerevive.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** A shareable offline customer request page, not a live hosted booking site. */
object CustomerBookingPage {
    fun escape(input: String): String = buildString {
        input.take(300).forEach { ch ->
            append(when (ch) {
                '&' -> "&amp;"; '<' -> "&lt;"; '>' -> "&gt;"
                '"' -> "&quot;"; '\'' -> "&#39;"
                else -> ch.toString()
            })
        }
    }

    fun ready(profile: BusinessProfile): Boolean =
        profile.name.isNotBlank() &&
            (CampaignRules.digits(profile.phone).length in 10..15 ||
                profile.email.trim().matches(Regex("[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,100}\\.[A-Za-z]{2,20}")))

    fun html(profile: BusinessProfile): String {
        require(ready(profile)) { "Add a business name and a valid contact phone or email first." }
        val company = escape(profile.name)
        val phone = CampaignRules.digits(profile.phone).takeIf { it.length in 10..15 }.orEmpty()
        val email = profile.email.trim().takeIf {
            it.matches(Regex("[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,100}\\.[A-Za-z]{2,20}"))
        }.orEmpty()
        return """
<!DOCTYPE html><html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="dark"><title>Service Request | $company</title>
<style>
*{box-sizing:border-box}body{margin:0;font:16px system-ui,Arial;background:#0b121c;color:#fff}
main{max-width:540px;margin:0 auto;padding:28px 20px 60px}
small,.sub{color:#adbbcd}h1{font-size:28px;margin:10px 0 14px}
.tag{font-weight:800;letter-spacing:2px;color:#4ade80;font-size:13px}
.card{background:#172335;border:1px solid #304153;border-radius:20px;padding:20px;margin-top:20px}
label{display:block;font-weight:600;font-size:14px;margin:14px 0 6px}
input,textarea{width:100%;border:1px solid #556679;border-radius:10px;background:#0b121c;color:#fff;
padding:13px;font:inherit;min-height:46px}textarea{resize:vertical}
button{background:#4ade80;color:#07151c;border:0;border-radius:12px;padding:15px;font:700 16px system-ui;
width:100%;margin-top:24px;cursor:pointer}
a{color:#89e7a4}footer{margin-top:26px;font-size:13px;line-height:1.6}
</style></head><body><main>
<div class="tag">SERVICE REQUEST</div><h1>$company</h1>
<p class="sub">Tell us what you need. This sends a request to our business;
it does not automatically reserve an appointment.</p>
<section class="card"><form id="request">
<label for="name">Your name *</label><input id="name" required maxlength="100" autocomplete="name">
<label for="phone">Phone number *</label><input id="phone" required maxlength="30" type="tel" autocomplete="tel">
<label for="zip">5-digit ZIP code *</label><input id="zip" required pattern="[0-9]{5}" inputmode="numeric" maxlength="5">
<label for="service">Service requested *</label><input id="service" required maxlength="120" placeholder="e.g. Pressure washing">
<label for="date">Preferred date *</label><input id="date" type="date" required>
<label for="time">Preferred time *</label><input id="time" type="time" required>
<label for="details">Details (optional)</label><textarea id="details" maxlength="750" rows="3"></textarea>
<button type="submit">Prepare my request</button></form></section>
<footer><strong>Privacy & booking:</strong> Your details remain on this page until
you choose to send them through your device's messaging or email app. Your messaging
provider may process them. Review the message before sending. A requested time is
not confirmed until we contact you directly. This is not a marketing subscription form.</footer>
</main><script>
(function(){
'use strict';
const businessPhone='$phone';
const businessEmail='$email';
const form=document.getElementById('request');
const day=document.getElementById('date');
day.min=new Date(Date.now()-new Date().getTimezoneOffset()*60000).toISOString().slice(0,10);
form.addEventListener('submit',function(event){
  event.preventDefault();
  if(!form.reportValidity())return;
  const val=id=>document.getElementById(id).value.trim();
  const msg='New service request' +
    '\nName: '+val('name')+'\nPhone: '+val('phone')+'\nZIP: '+val('zip')+
    '\nService: '+val('service')+'\nPreferred: '+val('date')+' '+val('time')+
    '\nDetails: '+val('details')+'\nPlease confirm availability before booking.';
  if(businessPhone){
    window.location.href='sms:'+businessPhone+'?body='+encodeURIComponent(msg);
  }else if(businessEmail){
    window.location.href='mailto:'+businessEmail+'?subject='+
        encodeURIComponent('New service request')+'&body='+encodeURIComponent(msg);
  }
});
})();
</script></body></html>
""".trimIndent()
    }

    fun share(context: Context, profile: BusinessProfile) {
        val folder = File(context.cacheDir, "booking_pages").apply { mkdirs() }
        val file = File(folder, "RouteRevive-Booking-Request.html")
        file.writeText(html(profile), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share booking request page"))
    }
}
