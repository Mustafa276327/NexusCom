package com.nexus.companion.nexus;

import android.app.admin.DeviceAdminReceiver;

/**
 * Cihaz yöneticisi alıcısı. Sihirbazdan etkinleştirilince NEXUS,
 * DevicePolicyManager.lockNow() ile ekranı kilitleyebilir
 * (bkz. NexusCommandHandler.ekranKilidi).
 */
public class NexusDeviceAdmin extends DeviceAdminReceiver {
}
