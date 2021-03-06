/**
 * Nextcloud Android client application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2017 Mario Danic
 * Copyright (C) 2017 Andy Scherzinger
 * Copyright (C) 2017 Nextcloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.owncloud.android.ui.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.google.gson.Gson;
import com.owncloud.android.R;
import com.owncloud.android.authentication.AccountUtils;
import com.owncloud.android.authentication.AuthenticatorActivity;
import com.owncloud.android.datamodel.ArbitraryDataProvider;
import com.owncloud.android.datamodel.PushConfigurationState;
import com.owncloud.android.datamodel.SyncedFolderProvider;
import com.owncloud.android.lib.common.UserInfo;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.lib.resources.users.GetRemoteUserInfoOperation;
import com.owncloud.android.ui.events.TokenPushEvent;
import com.owncloud.android.utils.DisplayUtils;
import com.owncloud.android.utils.PushUtils;
import com.owncloud.android.utils.ThemeUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.parceler.Parcels;

import butterknife.BindString;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * This Activity presents the user information.
 */
public class UserInfoActivity extends FileActivity {
    public static final String KEY_ACCOUNT = "ACCOUNT";

    private static final String TAG = UserInfoActivity.class.getSimpleName();
    private static final String KEY_USER_DATA = "USER_DATA";
    private static final String KEY_DIRECT_REMOVE = "DIRECT_REMOVE";

    private static final int KEY_DELETE_CODE = 101;

    @BindView(R.id.empty_list_view)
    public LinearLayout emptyContentContainer;

    @BindView(R.id.empty_list_view_text)
    public TextView emptyContentMessage;

    @BindView(R.id.empty_list_view_headline)
    public TextView emptyContentHeadline;

    @BindView(R.id.empty_list_icon)
    public ImageView emptyContentIcon;

    @BindView(R.id.user_info_view)
    public LinearLayout userInfoView;

    @BindView(R.id.user_icon)
    public ImageView avatar;

    @BindView(R.id.drawer_username)
    public TextView userName;

    @BindView(R.id.drawer_username_full)
    public TextView fullName;

    @BindView(R.id.phone_container)
    public View mPhoneNumberContainer;

    @BindView(R.id.phone_number)
    public TextView mPhoneNumberTextView;

    @BindView(R.id.phone_icon)
    public ImageView mPhoneNumberIcon;

    @BindView(R.id.email_container)
    public View mEmailContainer;

    @BindView(R.id.email_address)
    public TextView mEmailAddressTextView;

    @BindView(R.id.email_icon)
    public ImageView mEmailIcon;

    @BindView(R.id.address_container)
    public View mAddressContainer;

    @BindView(R.id.address)
    public TextView mAddressTextView;

    @BindView(R.id.address_icon)
    public ImageView mAddressIcon;

    @BindView(R.id.website_container)
    public View mWebsiteContainer;

    @BindView(R.id.website_address)
    public TextView mWebsiteTextView;

    @BindView(R.id.website_icon)
    public ImageView mWebsiteIcon;

    @BindView(R.id.twitter_container)
    public View mTwitterContainer;

    @BindView(R.id.twitter_handle)
    public TextView mTwitterHandleTextView;

    @BindView(R.id.twitter_icon)
    public ImageView mTwitterIcon;

    @BindView(R.id.empty_list_progress)
    public ProgressBar multiListProgressBar;

    @BindString(R.string.user_information_retrieval_error)
    public String sorryMessage;

    private float mCurrentAccountAvatarRadiusDimension;

    private Unbinder unbinder;

    private UserInfo userInfo;
    private Account account;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log_OC.v(TAG, "onCreate() start");
        super.onCreate(savedInstanceState);

        Bundle bundle = getIntent().getExtras();

        account = Parcels.unwrap(bundle.getParcelable(KEY_ACCOUNT));

        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_USER_DATA)) {
            userInfo = Parcels.unwrap(savedInstanceState.getParcelable(KEY_USER_DATA));
        }

        mCurrentAccountAvatarRadiusDimension = getResources().getDimension(R.dimen.nav_drawer_header_avatar_radius);

        setContentView(R.layout.user_info_layout);
        unbinder = ButterKnife.bind(this);

        setAccount(AccountUtils.getCurrentOwnCloudAccount(this));
        onAccountSet(false);

        boolean useBackgroundImage = URLUtil.isValidUrl(
                getStorageManager().getCapability(account.name).getServerBackground());

        setupToolbar(useBackgroundImage);
        updateActionBarTitleAndHomeButtonByString("");


        if (userInfo != null) {
            populateUserInfoUi(userInfo);
            emptyContentContainer.setVisibility(View.GONE);
            userInfoView.setVisibility(View.VISIBLE);
        } else {
            setMultiListLoadingMessage();
            fetchAndSetData();
        }

        setHeaderImage();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.user_info_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean retval = true;
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.change_password:
                changeAccountPassword(account);
                break;
            case R.id.delete_account:
                openAccountRemovalConfirmationDialog(account, getFragmentManager(), false);
                break;
            default:
                retval = super.onOptionsItemSelected(item);
                break;
        }
        return retval;
    }

    public void onDestroy() {
        super.onDestroy();
        unbinder.unbind();
    }

    private void setMultiListLoadingMessage() {
        if (emptyContentContainer != null) {
            emptyContentHeadline.setText(R.string.file_list_loading);
            emptyContentMessage.setText("");

            emptyContentIcon.setVisibility(View.GONE);
            emptyContentMessage.setVisibility(View.GONE);
            multiListProgressBar.getIndeterminateDrawable().setColorFilter(ThemeUtils.primaryColor(),
                    PorterDuff.Mode.SRC_IN);
            multiListProgressBar.setVisibility(View.VISIBLE);
        }
    }

    private void setErrorMessageForMultiList(String headline, String message) {
        if (emptyContentContainer != null && emptyContentMessage != null) {
            emptyContentHeadline.setText(headline);
            emptyContentMessage.setText(message);
            emptyContentIcon.setImageResource(R.drawable.ic_list_empty_error);

            multiListProgressBar.setVisibility(View.GONE);
            emptyContentIcon.setVisibility(View.VISIBLE);
            emptyContentMessage.setVisibility(View.VISIBLE);
        }
    }

    private void setHeaderImage() {
        if (getStorageManager().getCapability(account.name).getServerBackground() != null) {
            final AppBarLayout appBar = (AppBarLayout) findViewById(R.id.appbar);

            if (appBar != null) {
                String background = getStorageManager().getCapability(account.name).getServerBackground();

                if (URLUtil.isValidUrl(background)) {
                    // background image
                    SimpleTarget target = new SimpleTarget<Drawable>() {
                        @Override
                        public void onResourceReady(Drawable resource, GlideAnimation glideAnimation) {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                                appBar.setBackgroundDrawable(resource);
                            } else {
                                appBar.setBackground(resource);
                            }
                        }
                    };

                    Glide.with(this)
                            .load(background)
                            .centerCrop()
                            .placeholder(R.drawable.background)
                            .error(R.drawable.background)
                            .crossFade()
                            .into(target);
                } else if (!background.isEmpty()) {
                    // plain color
                    int color = Color.parseColor(background);
                    appBar.setBackgroundColor(color);
                }
            }
        }
    }

    private void populateUserInfoUi(UserInfo userInfo) {
        userName.setText(account.name);
        DisplayUtils.setAvatar(account, UserInfoActivity.this,
                mCurrentAccountAvatarRadiusDimension, getResources(), getStorageManager(), avatar);

        int tint = ThemeUtils.primaryColor(account);

        if (userInfo != null) {
            if (!TextUtils.isEmpty(userInfo.getDisplayName())) {
                fullName.setText(userInfo.getDisplayName());
            }

            populateUserInfoElement(mPhoneNumberContainer, mPhoneNumberTextView, userInfo.getPhone(),
                    mPhoneNumberIcon, tint);
            populateUserInfoElement(mEmailContainer, mEmailAddressTextView, userInfo.getEmail(), mEmailIcon, tint);
            populateUserInfoElement(mAddressContainer, mAddressTextView, userInfo.getAddress(), mAddressIcon, tint);

            populateUserInfoElement(
                    mWebsiteContainer,
                    mWebsiteTextView,
                    DisplayUtils.beautifyURL(userInfo.getWebpage()),
                    mWebsiteIcon,
                    tint);
            populateUserInfoElement(
                    mTwitterContainer,
                    mTwitterHandleTextView,
                    DisplayUtils.beautifyTwitterHandle(userInfo.getTwitter()),
                    mTwitterIcon,
                    tint);
        }
    }

    private void populateUserInfoElement(View container, TextView textView, String text, ImageView icon, @ColorInt int
            tint) {
        if (!TextUtils.isEmpty(text)) {
            textView.setText(text);
            DrawableCompat.setTint(icon.getDrawable(), tint);
        } else {
            container.setVisibility(View.GONE);
        }
    }

    private void changeAccountPassword(Account account) {
        // let the user update credentials with one click
        Intent updateAccountCredentials = new Intent(this, AuthenticatorActivity.class);
        updateAccountCredentials.putExtra(AuthenticatorActivity.EXTRA_ACCOUNT, account);
        updateAccountCredentials.putExtra(AuthenticatorActivity.EXTRA_ACTION,
                AuthenticatorActivity.ACTION_UPDATE_TOKEN);
        startActivity(updateAccountCredentials);
    }

    public static void openAccountRemovalConfirmationDialog(Account account, FragmentManager fragmentManager,
                                                            boolean removeDirectly) {
        UserInfoActivity.AccountRemovalConfirmationDialog dialog =
                UserInfoActivity.AccountRemovalConfirmationDialog.newInstance(account, removeDirectly);
        dialog.show(fragmentManager, "dialog");
    }

    public static class AccountRemovalConfirmationDialog extends DialogFragment {

        private Account account;

        public static UserInfoActivity.AccountRemovalConfirmationDialog newInstance(Account account,
                                                                                    boolean removeDirectly) {
            Bundle bundle = new Bundle();
            bundle.putParcelable(KEY_ACCOUNT, account);
            bundle.putBoolean(KEY_DIRECT_REMOVE, removeDirectly);

            UserInfoActivity.AccountRemovalConfirmationDialog dialog = new
                    UserInfoActivity.AccountRemovalConfirmationDialog();
            dialog.setArguments(bundle);

            return dialog;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            account = getArguments().getParcelable(KEY_ACCOUNT);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final boolean removeDirectly = getArguments().getBoolean(KEY_DIRECT_REMOVE);
            return new AlertDialog.Builder(getActivity(), R.style.Theme_ownCloud_Dialog)
                    .setTitle(R.string.delete_account)
                    .setMessage(getResources().getString(R.string.delete_account_warning, account.name))
                    .setIcon(R.drawable.ic_warning)
                    .setPositiveButton(R.string.common_ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    // remove contact backup job
                                    ContactsPreferenceActivity.cancelContactBackupJobForAccount(getActivity(), account);

                                    ContentResolver contentResolver = getActivity().getContentResolver();
                                    // delete all synced folder for an account
                                    SyncedFolderProvider syncedFolderProvider = new SyncedFolderProvider(
                                            contentResolver);
                                    syncedFolderProvider.deleteSyncFoldersForAccount(account);

                                    // disable daily backup
                                    ArbitraryDataProvider arbitraryDataProvider = new ArbitraryDataProvider(
                                            contentResolver);

                                    arbitraryDataProvider.storeOrUpdateKeyValue(account.name,
                                            ContactsPreferenceActivity.PREFERENCE_CONTACTS_AUTOMATIC_BACKUP,
                                            "false");


                                    String arbitraryDataPushString;

                                    if (!TextUtils.isEmpty(arbitraryDataPushString = arbitraryDataProvider.getValue(
                                            account, PushUtils.KEY_PUSH)) &&
                                            !TextUtils.isEmpty(getResources().getString(R.string.push_server_url))) {
                                        Gson gson = new Gson();
                                        PushConfigurationState pushArbitraryData = gson.fromJson(arbitraryDataPushString,
                                                PushConfigurationState.class);
                                        pushArbitraryData.setShouldBeDeleted(true);
                                        arbitraryDataProvider.storeOrUpdateKeyValue(account.name, PushUtils.KEY_PUSH,
                                                gson.toJson(pushArbitraryData));
                                        EventBus.getDefault().post(new TokenPushEvent());
                                    }


                                    if (getActivity() != null && !removeDirectly) {
                                        Bundle bundle = new Bundle();
                                        bundle.putParcelable(KEY_ACCOUNT, Parcels.wrap(account));
                                        Intent intent = new Intent();
                                        intent.putExtras(bundle);
                                        getActivity().setResult(KEY_DELETE_CODE, intent);
                                        getActivity().finish();
                                    } else {
                                        AccountManager am = (AccountManager) getActivity()
                                                .getSystemService(ACCOUNT_SERVICE);

                                        am.removeAccount(account, null, null);

                                        Intent start = new Intent(getActivity(), FileDisplayActivity.class);
                                        start.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                        startActivity(start);

                                    }

                                }
                            })
                    .setNegativeButton(R.string.common_cancel, null)
                    .create();
        }
    }

    private void fetchAndSetData() {
        Thread t = new Thread(new Runnable() {
            public void run() {

                RemoteOperation getRemoteUserInfoOperation = new GetRemoteUserInfoOperation();
                RemoteOperationResult result = getRemoteUserInfoOperation.execute(account, UserInfoActivity.this);

                if (result.isSuccess() && result.getData() != null) {
                    userInfo = (UserInfo) result.getData().get(0);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            populateUserInfoUi(userInfo);

                            emptyContentContainer.setVisibility(View.GONE);
                            userInfoView.setVisibility(View.VISIBLE);
                        }
                    });
                } else {
                    // show error
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setErrorMessageForMultiList(sorryMessage, result.getLogMessage());
                        }
                    });
                    Log_OC.d(TAG, result.getLogMessage());
                }
            }
        });

        t.start();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (userInfo != null) {
            outState.putParcelable(KEY_USER_DATA, Parcels.wrap(userInfo));
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(TokenPushEvent event) {
        PushUtils.pushRegistrationToServer();
    }
}
