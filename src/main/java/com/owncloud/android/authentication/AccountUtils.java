/**
 *   ownCloud Android client application
 *
 *   Copyright (C) 2012  Bartek Przybylski
 *   Copyright (C) 2016 ownCloud Inc.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.owncloud.android.authentication;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.owncloud.android.MainApp;
import com.owncloud.android.datamodel.ArbitraryDataProvider;
import com.owncloud.android.datamodel.FileDataStorageManager;
import com.owncloud.android.lib.common.accounts.AccountTypeUtils;
import com.owncloud.android.lib.common.accounts.AccountUtils.Constants;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.lib.resources.status.OwnCloudVersion;
import com.owncloud.android.operations.GetCapabilitiesOperarion;
import com.owncloud.android.ui.activity.ManageAccountsActivity;

import java.util.Locale;

public class AccountUtils {

    private static final String TAG = AccountUtils.class.getSimpleName();

    public static final String WEBDAV_PATH_4_0_AND_LATER = "/remote.php/webdav";
    public static final String DAV_PATH = "/remote.php/dav";
    private static final String ODAV_PATH = "/remote.php/odav";
    private static final String SAML_SSO_PATH = "/remote.php/webdav";
    public static final String STATUS_PATH = "/status.php";

    public static final int ACCOUNT_VERSION = 1;
    public static final int ACCOUNT_VERSION_WITH_PROPER_ID = 2;

    /**
     * Can be used to get the currently selected ownCloud {@link Account} in the
     * application preferences.
     * 
     * @param   context     The current application {@link Context}
     * @return              The ownCloud {@link Account} currently saved in preferences, or the first 
     *                      {@link Account} available, if valid (still registered in the system as ownCloud 
     *                      account). If none is available and valid, returns null.
     */
    public static Account getCurrentOwnCloudAccount(Context context) {
        Account[] ocAccounts = getAccounts(context);
        Account defaultAccount = null;

        ArbitraryDataProvider arbitraryDataProvider = new ArbitraryDataProvider(context.getContentResolver());

        SharedPreferences appPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String accountName = appPreferences.getString("select_oc_account", null);

        // account validation: the saved account MUST be in the list of ownCloud Accounts known by the AccountManager
        if (accountName != null) {
            for (Account account : ocAccounts) {
                if (account.name.equals(accountName)) {
                    defaultAccount = account;
                    break;
                }
            }
        }

        if (defaultAccount == null && ocAccounts.length > 0) {
            // take first which is not pending for removal account as fallback
            for (Account account: ocAccounts) {
                boolean pendingForRemoval = arbitraryDataProvider.getBooleanValue(account,
                        ManageAccountsActivity.PENDING_FOR_REMOVAL);

                if (!pendingForRemoval) {
                    defaultAccount = account;
                    break;
                }
            }
        }

        return defaultAccount;
    }

    public static Account[] getAccounts(Context context) {
        AccountManager accountManager = AccountManager.get(context);
        return accountManager.getAccountsByType(MainApp.getAccountType());
    }

    
    public static boolean exists(Account account, Context context) {
        Account[] ocAccounts = getAccounts(context);

        if (account != null && account.name != null) {
            int lastAtPos = account.name.lastIndexOf("@");
            String hostAndPort = account.name.substring(lastAtPos + 1);
            String username = account.name.substring(0, lastAtPos);
            String otherHostAndPort;
            String otherUsername;
            Locale currentLocale = context.getResources().getConfiguration().locale;
            for (Account otherAccount : ocAccounts) {
                lastAtPos = otherAccount.name.lastIndexOf("@");
                otherHostAndPort = otherAccount.name.substring(lastAtPos + 1);
                otherUsername = otherAccount.name.substring(0, lastAtPos);
                if (otherHostAndPort.equals(hostAndPort) &&
                        otherUsername.toLowerCase(currentLocale).
                            equals(username.toLowerCase(currentLocale))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * returns the user's name based on the account name.
     *
     * @param accountName the account name
     * @return the user's name
     */
    public static String getAccountUsername(String accountName) {
        if (accountName != null) {
            return accountName.substring(0, accountName.lastIndexOf('@'));
        } else {
            return null;
        }
    }
    
    /**
     * Returns owncloud account identified by accountName or null if it does not exist.
     * @param context
     * @param accountName name of account to be returned
     * @return owncloud account named accountName
     */
    public static Account getOwnCloudAccountByName(Context context, String accountName) {
        Account[] ocAccounts = AccountManager.get(context).getAccountsByType(
                MainApp.getAccountType());
        for (Account account : ocAccounts) {
            if(account.name.equals(accountName)) {
                return account;
            }
        }
        return null;
    }


    public static boolean setCurrentOwnCloudAccount(final Context context, String accountName) {
        boolean result = false;
        if (accountName != null) {
            boolean found;
            for (final Account account : getAccounts(context)) {
                found = (account.name.equals(accountName));
                if (found) {
                    SharedPreferences.Editor appPrefs = PreferenceManager.getDefaultSharedPreferences(context).edit();
                    appPrefs.putString("select_oc_account", accountName);

                    // update credentials
                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            FileDataStorageManager storageManager = new FileDataStorageManager(account,
                                    context.getContentResolver());
                            GetCapabilitiesOperarion getCapabilities = new GetCapabilitiesOperarion();
                            RemoteOperationResult updateResult = getCapabilities.execute(storageManager, context);
                            Log_OC.w(TAG, "Update Capabilities: " + updateResult.isSuccess());
                        }
                    });

                    t.start();

                    appPrefs.apply();
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    public static void resetOwnCloudAccount(Context context) {
        SharedPreferences.Editor appPrefs = PreferenceManager.getDefaultSharedPreferences(context).edit();
        appPrefs.putString("select_oc_account", null);

        appPrefs.apply();
    }

    /**
     * Returns the proper URL path to access the WebDAV interface of an ownCloud server,
     * according to its version and the authorization method used.
     * 
     * @param   version         Version of ownCloud server.
     * @param   authTokenType   Authorization token type, matching some of the AUTH_TOKEN_TYPE_* constants in
     *                          {@link AccountAuthenticator}.
     * @return                  WebDAV path for given OC version and authorization method, null if OC version
     *                          is unknown; versions prior to ownCloud 4 are not supported anymore
     */
    public static String getWebdavPath(OwnCloudVersion version, String authTokenType) {
        if (version != null) {
            if (AccountTypeUtils.getAuthTokenTypeAccessToken(MainApp.getAccountType()).equals(authTokenType)) {
                return ODAV_PATH;
            }
            if (AccountTypeUtils.getAuthTokenTypeSamlSessionCookie(MainApp.getAccountType()).equals(authTokenType)) {
                return SAML_SSO_PATH;
            }

            return WEBDAV_PATH_4_0_AND_LATER;
        }
        return null;
    }

    
    public static String trimWebdavSuffix(String url) {
        while(url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        int pos = url.lastIndexOf(WEBDAV_PATH_4_0_AND_LATER);
        if (pos >= 0) {
            url = url.substring(0, pos);

        } else {
            pos = url.lastIndexOf(ODAV_PATH);
            if (pos >= 0) {
                url = url.substring(0, pos);
            }
        }
        return url;
    }

    /**
     * Access the version of the OC server corresponding to an account SAVED IN THE ACCOUNTMANAGER
     *
     * @param   account     ownCloud account
     * @return              Version of the OC server corresponding to account, according to the data saved
     *                      in the system AccountManager
     */
    public static OwnCloudVersion getServerVersion(Account account) {
        OwnCloudVersion serverVersion = null;
        if (account != null) {
            AccountManager accountMgr = AccountManager.get(MainApp.getAppContext());
            String serverVersionStr = accountMgr.getUserData(account, Constants.KEY_OC_VERSION);
            if (serverVersionStr != null) {
                serverVersion = new OwnCloudVersion(serverVersionStr);
            }
        }
        return serverVersion;
    }

    public static boolean hasSearchUsersSupport(Account account){
        OwnCloudVersion serverVersion = getServerVersion(account);
        return (serverVersion != null && serverVersion.isSearchUsersSupported());
    }

    public static boolean hasSearchSupport(Account account) {
        OwnCloudVersion serverVersion = getServerVersion(account);
        return (serverVersion != null && serverVersion.isSearchSupported());
    }
}
