# Room, WorkManager and Compose ship their own keep rules.
# Keep the notification listener (referenced only from the manifest).
-keep class com.example.moneyapp.capture.TransactionListenerService { *; }
# PDFBox creates its security handlers (locked PDFs) by reflection; JPEG 2000 support is an optional extra.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.gemalto.jp2.**
