package com.avoscloud.chat.ui.profile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.avos.avoscloud.AVException;
import com.avos.avoscloud.AVUser;
import com.avos.avoscloud.SaveCallback;
import com.avos.avoscloud.im.v2.AVIMClient;
import com.avos.avoscloud.im.v2.callback.AVIMClientCallback;
import com.avoscloud.chat.R;
import com.avoscloud.chat.entity.avobject.User;
import com.avoscloud.chat.service.UpdateService;
import com.avoscloud.chat.service.UserService;
import com.avoscloud.chat.ui.base_activity.BaseFragment;
import com.avoscloud.chat.util.PathUtils;
import com.avoscloud.chat.util.PhotoUtils;
import com.avoscloud.chat.util.SimpleNetTask;
import com.avoscloud.chat.util.Utils;
import com.avoscloud.leanchatlib.controller.ChatManager;
import com.avoscloud.leanchatlib.db.DBHelper;
import com.avoscloud.chat.util.Logger;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by lzw on 14-9-17.
 */
public class ProfileFragment extends BaseFragment implements View.OnClickListener {
  private static final int IMAGE_PICK_REQUEST = 1;
  private static final int CROP_REQUEST = 2;
  TextView usernameView, genderView;
  ImageView avatarView;
  View usernameLayout, avatarLayout, logoutLayout,
      genderLayout, notifyLayout, updateLayout;
  ChatManager chatManager;
  SaveCallback saveCallback = new SaveCallback() {
    @Override
    public void done(AVException e) {
      refresh();
    }
  };

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.profile_fragment, container, false);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    headerLayout.showTitle(R.string.profile_title);
    chatManager = ChatManager.getInstance();
    findView();
    refresh();
  }

  private void refresh() {
    AVUser curUser = AVUser.getCurrentUser();
    usernameView.setText(curUser.getUsername());
    genderView.setText(User.getGenderDesc(curUser));
    UserService.displayAvatar(User.getAvatarUrl(curUser), avatarView);
  }

  private void findView() {
    View fragmentView = getView();
    usernameView = (TextView) fragmentView.findViewById(R.id.username);
    avatarView = (ImageView) fragmentView.findViewById(R.id.avatar);
    usernameLayout = fragmentView.findViewById(R.id.usernameLayout);
    avatarLayout = fragmentView.findViewById(R.id.avatarLayout);
    logoutLayout = fragmentView.findViewById(R.id.logoutLayout);
    genderLayout = fragmentView.findViewById(R.id.sexLayout);
    notifyLayout = fragmentView.findViewById(R.id.notifyLayout);
    genderView = (TextView) fragmentView.findViewById(R.id.sex);
    updateLayout = fragmentView.findViewById(R.id.updateLayout);

    avatarLayout.setOnClickListener(this);
    logoutLayout.setOnClickListener(this);
    genderLayout.setOnClickListener(this);
    notifyLayout.setOnClickListener(this);
    updateLayout.setOnClickListener(this);
  }

  @Override
  public void onClick(View v) {
    int id = v.getId();
    if (id == R.id.avatarLayout) {
      Intent intent = new Intent(Intent.ACTION_PICK, null);
      intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
      startActivityForResult(intent, IMAGE_PICK_REQUEST);
    } else if (id == R.id.logoutLayout) {
      DBHelper.getCurrentUserInstance().closeHelper();
      chatManager.closeWithCallback(new AVIMClientCallback() {
        @Override
        public void done(AVIMClient avimClient, AVException e) {
        }
      });
      AVUser.logOut();
      getActivity().finish();
    } else if (id == R.id.sexLayout) {
      showSexChooseDialog();
    } else if (id == R.id.notifyLayout) {
      Utils.goActivity(ctx, ProfileNotifySettingActivity.class);
    } else if (id == R.id.updateLayout) {
      UpdateService updateService = UpdateService.getInstance(getActivity());
      updateService.showSureUpdateDialog();
    }
  }

  private void showSexChooseDialog() {
    AVUser user = AVUser.getCurrentUser();
    int checkItem = User.getGender(user) == User.Gender.Male ? 0 : 1;
    new AlertDialog.Builder(ctx).setTitle(R.string.profile_sex)
        .setSingleChoiceItems(User.genderStrings, checkItem, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            User.Gender gender;
            if (which == 0) {
              gender = User.Gender.Male;
            } else {
              gender = User.Gender.Female;
            }
            UserService.saveSex(gender, saveCallback);
            dialog.dismiss();
          }
        }).setNegativeButton(R.string.chat_common_cancel, null).show();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    Logger.d("on Activity result " + requestCode + " " + resultCode);
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == Activity.RESULT_OK) {
      if (requestCode == IMAGE_PICK_REQUEST) {
        Uri uri = data.getData();
        startImageCrop(uri, 200, 200, CROP_REQUEST);
      } else if (requestCode == CROP_REQUEST) {
        final String path = saveCropAvatar(data);
        new SimpleNetTask(ctx) {
          @Override
          protected void doInBack() throws Exception {
            UserService.saveAvatar(path);
          }

          @Override
          protected void onSucceed() {
            refresh();
          }
        }.execute();

      }
    }
  }

  public Uri startImageCrop(Uri uri, int outputX, int outputY,
                            int requestCode) {
    Intent intent = null;
    intent = new Intent("com.android.camera.action.CROP");
    intent.setDataAndType(uri, "image/*");
    intent.putExtra("crop", "true");
    intent.putExtra("aspectX", 1);
    intent.putExtra("aspectY", 1);
    intent.putExtra("outputX", outputX);
    intent.putExtra("outputY", outputY);
    intent.putExtra("scale", true);
    String outputPath = PathUtils.getAvatarTmpPath();
    Uri outputUri = Uri.fromFile(new File(outputPath));
    intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
    intent.putExtra("return-data", true);
    intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
    intent.putExtra("noFaceDetection", false); // face detection
    startActivityForResult(intent, requestCode);
    return outputUri;
  }

  private String saveCropAvatar(Intent data) {
    Bundle extras = data.getExtras();
    String path = null;
    if (extras != null) {
      Bitmap bitmap = extras.getParcelable("data");
      if (bitmap != null) {
        bitmap = PhotoUtils.toRoundCorner(bitmap, 10);
        String filename = new SimpleDateFormat("yyMMddHHmmss")
            .format(new Date());
        path = PathUtils.getAvatarDir() + filename;
        Logger.d("save bitmap to " + path);
        PhotoUtils.saveBitmap(PathUtils.getAvatarDir(), filename,
            bitmap, true);
        if (bitmap != null && bitmap.isRecycled() == false) {
          //bitmap.recycle();
        }
      }
    }
    return path;
  }
}
