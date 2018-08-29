package edu.sfsu.csc780.chathub.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.Display;
import android.view.View;
import android.widget.ImageView;

import edu.sfsu.csc780.chathub.R;


public class ImageDialogFragment extends DialogFragment {

    private static final String LOG_TAG = ImageDialogFragment.class.getSimpleName();
    private static String PHOTO_BITMAP = "Photo_bitmap";
    private static Bitmap mPhotoBitmap;
    private static int mScaledWidth;
    private static int mScaledHeight;
    private static int SIDE_MARGIN = 200;


    static ImageDialogFragment newInstance(Bitmap bitmap){
        ImageDialogFragment f = new ImageDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(PHOTO_BITMAP, bitmap);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        mPhotoBitmap = getArguments().getParcelable(PHOTO_BITMAP);
        int imageWidth = mPhotoBitmap.getWidth();
        int imageHeight = mPhotoBitmap.getHeight();
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        mScaledWidth = (int)((double)width - SIDE_MARGIN);
        mScaledHeight = (int)((double)imageHeight / (double)imageWidth * mScaledWidth);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState){
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        View view = getActivity().getLayoutInflater().inflate(R.layout.image_dialog_layout, null);

        ImageView photoImageView = (ImageView) view.findViewById(R.id.photoImageView);

        photoImageView.getLayoutParams().width = mScaledWidth;
        photoImageView.getLayoutParams().height = mScaledHeight;

        builder.setView(view);

        photoImageView.setImageBitmap(mPhotoBitmap);

        final Dialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(
                new ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.show();
        return dialog;
    }
}
