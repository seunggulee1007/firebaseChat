package com.example.firebasechatexam;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity implements  GoogleApiClient.OnConnectionFailedListener{

    private FirebaseRecyclerAdapter<ChatMessage, MessageViewHolder> mFirebaseAdpater;
    private DatabaseReference mFirebaseDatabaseReference;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private EditText mMessageEditText;
    public static final String MESSAGE_CHILD = "messages";
    public static final String IMAGE_URL = "images/";
    private GoogleApiClient mGoogleApiClient;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;
    private Button btChoose;
    private Uri filePath;
    private ImageView ivPreview;
    private InputMethodManager inputMethodManager;
    private String filename;
    private static final String TAG = "MainActivity";
    private String name;
    private String mUsername;
    private String mPhotoUrl;

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(this,"Google Paly Services error.", Toast.LENGTH_SHORT).show();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder{
        TextView messageTextView;
        ImageView messageImageView;
        TextView messengerTextView;
        CircleImageView messengerImageView;
        public MessageViewHolder(View v){
            super(v);
            messageTextView = itemView.findViewById(R.id.messageTextView);              // 메시지 텍스트 뷰
            messageImageView = itemView.findViewById(R.id.messageImageView);            // 메시지 이미지 뷰
            messengerTextView = itemView.findViewById(R.id.messengerTextView);          // 메신저 텍스트 뷰(아이디)
            messengerImageView = itemView.findViewById(R.id.messengerImageView);        // 메신저 이미지 뷰 (프로필 사진)
        }
    }

    private RecyclerView mMessageRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btChoose = findViewById(R.id.bt_choose);
        mFirebaseDatabaseReference = FirebaseDatabase.getInstance().getReference();
        mMessageEditText = findViewById(R.id.message_edit);
        mGoogleApiClient = new GoogleApiClient.Builder(this).enableAutoManage(this,this).addApi(Auth.GOOGLE_SIGN_IN_API).build();
        inputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        mMessageEditText.setOnFocusChangeListener(new View.OnFocusChangeListener(){
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(!hasFocus){
                    Toast.makeText(MainActivity.this,"하하", Toast.LENGTH_SHORT).show();
                    inputMethodManager.hideSoftInputFromWindow(mMessageEditText.getWindowToken(),0);
                }else{
                    Toast.makeText(MainActivity.this,"허허", Toast.LENGTH_SHORT).show();
                }
            }
        });
        // Firebase 인증 초기화
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();
        if(mFirebaseUser == null){
            // 인증이 안 되어있다면 인증 화면으로 이동
            startActivity(new Intent(this,SignInActivity.class));
            finish();
            return;
        }else{
            mUsername = mFirebaseUser.getDisplayName();
            if(mFirebaseUser.getPhotoUrl() != null){
                mPhotoUrl = mFirebaseUser.getPhotoUrl().toString();
            }
        }
        mMessageRecyclerView = findViewById(R.id.message_recycler_view);
        ivPreview = findViewById(R.id.iv_preview);
        findViewById(R.id.send_button).setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                String message = mMessageEditText.getText().toString();

                if(filePath != null){
                    uploadFile();
                    ivPreview.setImageBitmap(null);
                }else if("".equals(message)){
                    Toast.makeText(MainActivity.this,"메시지를 입력해 주세요",Toast.LENGTH_SHORT).show();
                    return;
                }
                ChatMessage chatMessage = new ChatMessage(message,mUsername,mPhotoUrl, filename);
                mFirebaseDatabaseReference.child(MESSAGE_CHILD).push().setValue(chatMessage);
                mMessageEditText.setText("");
                inputMethodManager.hideSoftInputFromWindow(mMessageEditText.getWindowToken(),0);


            }
        });


        btChoose.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent,"이미지를 선택하세요"),0);
            }
        });
        // 쿼리 수행 위치
        Query query = mFirebaseDatabaseReference.child(MESSAGE_CHILD);

        // 옵션
        FirebaseRecyclerOptions<ChatMessage> options =
        new FirebaseRecyclerOptions.Builder<ChatMessage>().setQuery(query,ChatMessage.class).build();

        // 어댑터
        mFirebaseAdpater = new FirebaseRecyclerAdapter<ChatMessage, MessageViewHolder>(options) {           // 리사이클(리스트) 뷰 설정
            @Override
            protected void onBindViewHolder(MessageViewHolder holder, int position, ChatMessage model) {
                String user_nm = model.getName();                       // 유저 이름
                holder.messageTextView.setText(model.getText());        // 메시지 내용
                holder.messengerTextView.setText(user_nm);              // 유저 이름 설정

                if(mUsername.equals(user_nm)){                                  // 사용자의 이름과 내가 로그인 한 이름이 같다면

                    holder.messengerTextView.setBackgroundColor(Color.YELLOW);  // 내 이름은 노란줄
                }
                if(model.getPhotoUrl() == null){
                    holder.messengerImageView
                            .setImageDrawable(ContextCompat
                                    .getDrawable(MainActivity.this, R.drawable.ic_account_circle_black_24dp));
                } else {
                    Glide.with(MainActivity.this)
                            .load(model.getPhotoUrl())
                            .into(holder.messengerImageView);
                }
                if(model.getImageUrl() != null){
                    storageRef = FirebaseStorage.getInstance()
                            .getReferenceFromUrl("gs://fir-chatexam-4686d.appspot.com/images")
                            .child(model.getImageUrl());
                    Glide.with(MainActivity.this)
                            .load(storageRef)
                            .into(holder.messageImageView);
                }
                name = model.getName();
                holder.messageTextView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if(mUsername.equals(name)){
                            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                            builder.setTitle("알림");
                            builder.setMessage("글을 삭제하시겠습니까?");
                            builder.setNegativeButton("취소",null);
                            builder.setPositiveButton("삭제", new DialogInterface.OnClickListener(){

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    DatabaseReference databaseReference = mFirebaseDatabaseReference.child(MESSAGE_CHILD);
                                    Toast.makeText(MainActivity.this, databaseReference.push().getKey(), Toast.LENGTH_SHORT).show();
                                    String key = databaseReference.push().getKey();
                                    mFirebaseDatabaseReference.child(MESSAGE_CHILD).child(key).removeValue().addOnSuccessListener(new OnSuccessListener<Void>() {
                                        @Override
                                        public void onSuccess(Void aVoid) {
                                            Toast.makeText(MainActivity.this, "삭제 성공",Toast.LENGTH_SHORT).show();
                                        }
                                    }).addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Toast.makeText(MainActivity.this,"삭제 실패",Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            });
                            builder.show();
                        }else{
                            return false;
                        }
                        return true;
                    }
                });
            }


            @NonNull
            @Override
            public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
                return new MessageViewHolder(view);
            }
        };

        // 리사이클러뷰에 레이아웃 매니저와 어댑터 설정
        mMessageRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mMessageRecyclerView.setAdapter(mFirebaseAdpater);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.main,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch(item.getItemId()){
            case R.id.sign_out_menu:
            mFirebaseAuth.signOut();
            Auth.GoogleSignInApi.signOut(mGoogleApiClient);
            mUsername = "";
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart(){
        super.onStart();
        // firebaseRecyclerAdapter 실시간 쿼리 시작
        mFirebaseAdpater.startListening();
    }

    @Override
    protected void onStop(){
        super.onStop();
        mFirebaseAdpater.stopListening();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //request코드가 0이고 OK를 선택했고 data에 뭔가가 들어 있다면
        if(requestCode == 0 && resultCode == RESULT_OK){
            filePath = data.getData();
            Log.d(TAG, "uri:" + filePath);
            try {
                //Uri 파일을 Bitmap으로 만들어서 ImageView에 집어 넣는다.
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), filePath);
                ivPreview.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //upload the file
    private void uploadFile() {
        //업로드할 파일이 있으면 수행
        if (filePath != null) {
            //업로드 진행 Dialog 보이기
            final ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setTitle("업로드중...");
            progressDialog.show();

            //storage
            storage = FirebaseStorage.getInstance();

            //Unique한 파일명을 만들자.
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMHH_mmss");
            Date now = new Date();
            filename = formatter.format(now) + ".png";
            //storage 주소와 폴더 파일명을 지정해 준다.
            storageRef = storage.getReferenceFromUrl("gs://fir-chatexam-4686d.appspot.com").child("images/" + filename);
            storageRef.putFile(filePath)
                    //성공시
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            progressDialog.dismiss(); //업로드 진행 Dialog 상자 닫기
                            Toast.makeText(getApplicationContext(), "업로드 완료!", Toast.LENGTH_SHORT).show();
                        }
                    })
                    //실패시
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            progressDialog.dismiss();
                            Toast.makeText(getApplicationContext(), "업로드 실패!", Toast.LENGTH_SHORT).show();
                        }
                    })
                    //진행중
                    .addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                            @SuppressWarnings("VisibleForTests")
                                    double progress = (100 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                            //dialog에 진행률을 퍼센트로 출력해 준다
                            progressDialog.setMessage("Uploaded " + ((int) progress) + "% ...");
                        }
                    });
        } else {
            Toast.makeText(getApplicationContext(), "파일을 먼저 선택하세요.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("알림");
        builder.setMessage("앱을 종료하시겠습니까?");
        builder.setNegativeButton("취소",null);
        builder.setPositiveButton("종료", new DialogInterface.OnClickListener(){

            @Override
            public void onClick(DialogInterface dialog, int which) {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
        builder.show();
    }

}
