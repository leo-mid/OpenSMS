# OpenSMS
This is a free open source messaging app for Android. Its main purpose is to remove google and others
from reading every message sent and received in their default OS messaging apps. This app does not
communicate with any third party services. Doesn't even need Google Play Services to run!

## Current Features
As this app is still well into its early development phase there are currently a lot of missing features.
As of version 1.4.0 there is:
* Support for SMS (1.0.0)
* Support for MMS (1.1.0)
* Conversation Lists (1.1.0)
* Contacts shown in Conversation Lists (1.1.0)
* Live messaging support (Updates when message is received) (1.1.0)
* Creating new conversations (1.1.1)
* Video Playback (1.1.1)
* Notifications (1.2.0)
* Seeing unread messages (1.2.0)
* Deleting conversations (1.3.0)
* Group conversations (1.3.0)
* Uses Material 3 throughout (1.4.0)
* Start a call via the app (Using default calling app) (1.4.0)
* Blocking Numbers (1.4.0)
* End-to-End Encryption (1.4.0) - Untested don't have two android devices. Please lmk if any issues.
* Creating Contacts (1.4.0)

## End-to-End Encryption (E2EE)
OpenSMS supports  end-to-end encryption for SMS and MMS messages using a hybrid encryption scheme.

### How it Works
*   **Hybrid Encryption**: Every message is encrypted with a unique AES-256 session key (GCM mode). This session key is then encrypted using the recipient's RSA-2048 public key.
*   **Secure Key Storage**: Your private RSA key is generated and stored securely within the `AndroidKeyStore`, ensuring it never leaves your device.
*   **Group Support**: For group chats, the session key is encrypted multiple times—once for every participant.
*   **Self-Decryption**: The app automatically includes your own public key in the message header, allowing you to decrypt and read your own sent messages.

### How to Use
1.  **Exchange Keys**: Open a conversation, tap the three-dot menu, and select **Share Encryption Key**. This sends your public key to the contact.
2.  **Wait for Response**: Once the other person shares their key back, the app automatically saves it.
3.  **Enable Encryption**: Tap the three-dot menu and select **Encrypt Conversation**.
4.  **Send**: When you see the lock icon next to the person name you are good to go.

## Upcoming Features
These features are coming soon:
* Deleting individual messages
* Message actions such as saving MMS to device
* Message Reactions

## Permissions
The app needs the following permissions to function:
* SMS (required)
* Camera (optional)
* Contacts (optional)
* Photo Gallery (optional)
* Call Phone (optional)

## How to Install
### Method 1: APK
All the different versions APKS are provided in this GitHub in the [releases](https://github.com/leo-mid/OpenSMS/releases)
menu.
* Download the APK file
* Then install it!

### Method 2: Obtainium
Obtainium is an app that lets you download from any sources including GitHub.
* Enter this apps GitHub URL into the app source url box or search `leo-mid/OpenSMS` with parameters to search GitHub
* Select `leo-mid/OpenSMS`
* Next to App Source Url tap the plus sign
* Then select Install when it's done downloading

## Bug/Issue Reporting
Please report any issues when using this app so they can be tracked and worked on.