# BudsPop 🎧

Earbuds connect hote hi ek **anime-style pop-up** (chibi character, buds snap in, "Buds Connected!"), aur notification bar mein **battery (left / right / case) + play/pause/next + volume**.

Original artwork, drawn in code. Koi boAt trademark ya asset nahi hai. Ye ek **sideload APK** hai (Android 8.0+), Play Store ke liye nahi.

## Install kaise karein
1. Release page se `BudsPop-v1.0.apk` download karo.
2. Phone pe file kholo. "Unknown apps se install" allow karna pad sakta hai (Chrome / Files app ke liye).
3. App kholo aur neeche wale setup steps ek-ek karke karo.

## Setup steps (ek baar)
1. **Bluetooth access**: "Open / allow" dabao, Allow karo. (Android 12+ ke liye zaroori)
2. **Notifications**: Allow karo. Battery notification isi se dikhegi. (Android 13+)
3. **Show over other apps**: toggle ON karo. Ye pop-up ke liye hai. Na karo to pop-up ki jagah heads-up notification aayega.
4. **Background mein chalne do**: "Allow" karo taki phone Bluetooth wala service band na kare.
5. **Media controls access**: BudsPop ko Notification access do. Isse notification ke Play/Pause/Next us player par jaate hain jo abhi chal raha hai. Ye optional hai.
6. **Start BudsPop** dabao. Ab earbuds connect karo.

Settings mein theme (Sakura / Neon Night / Sunny) badal sakte ho, kaun sa device trigger kare (default: koi bhi audio device), sound/vibration on/off, aur case battery dikhana ya nahi. **Test pop-up** button se bina earbuds ke bhi animation dekh sakte ho.

## Battery: ईमानदारी wali baat (honest limits)
- Android normally **ek hi battery number** deta hai headset ke liye, left/right/case alag nahi.
- BudsPop teen standard routes try karta hai:
  1. Android ka system battery broadcast (kuch phones/Android 13+ pe aata hai).
  2. Apple-style HFP battery event (AT+IPHONEACCEV). Phone ka Bluetooth stack ise forward kare tabhi.
  3. Standard Battery Service (GATT 0x180F). Sirf BLE-capable earbuds ye dete hain.
- Jo value nahi aati, wahan **`--`** dikhta hai. Hum koi number invent nahi karte.
- **Left / Right / Case** tabhi dikhega jab earbuds exact wo value report karein. Zyadatar TWS earbuds ke liye ye `--` hi rahega. Aisa hona normal hai.
- Jab ek combined number aata hai, notification neeche "Buds: xx%" likh deta hai.

## Jo nahi hai (not available)
- boAt ka apna protocol: ANC on/off, EQ, sound mode, Game mode, custom touch controls. Ye boAt ke proprietary protocol se aate hain, Android apps ke liye open nahi hain. Inhe **fake nahi kiya gaya**.
- Play/Pause/Next sirf tab kaam karega jab koi music/video app chal raha ho aur Notification access diya ho.

## Build kaise hota hai
- Kotlin nahi, sirf Java + Android SDK (compile SDK 34, min SDK 26). Koi extra library nahi.
- `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Local `local.properties` mein `sdk.dir=` apna Android SDK path daalo.
- Debug key se sign hai (sideload ke liye). Play Store release ke liye apni key alag se lagni chahiye.
- `BudsScene.java` ka animation Canvas primitives se bana hai, isliye ek chhota desktop renderer bhi isi file ko chala ke frames bana sakta hai (preview.mp4 isi tarah bana).

## Testing status
- Build: pass (debug APK, 32 KB). Manifest permissions aapt se check kiye.
- **Asli phone pe test nahi hua.** Is box pe KVM nahi hai to emulator bhi nahi chala. Bluetooth event aur overlay behavior phone pe confirm karna baaki hai. Agar kuch na chale to batana, fix karenge.

## Permissions (kyun)
- `BLUETOOTH_CONNECT`: connected device ka naam aur connect event padhne ke liye
- `POST_NOTIFICATIONS`: battery notification aur fallback pop-up
- `SYSTEM_ALERT_WINDOW`: full-width pop-up overlay
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE`: background mein Bluetooth events sunne ke liye
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: phone service ko kill na kare
- `RECEIVE_BOOT_COMPLETED`: reboot ke baad BudsPop wapas chale (agar pehle on tha)
- Notification access (`BudsListener`): play/pause/next ke liye

Code: abhi koi open-source license nahi hai, Noor ke personal use ke liye.
