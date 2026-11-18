package com.example.myfit.flutter;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** Synthetic README data only; refuses physical devices and non-stock libraries. */
public class DocumentationFixtureInstrumentation extends Instrumentation {
    private Context context;
    private final Uri store = Uri.parse("content://com.example.myfit.rust-workout");
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private JSONObject obj(Object... fields) throws Exception {
        JSONObject result=new JSONObject(); for(int i=0;i<fields.length;i+=2) result.put((String)fields[i],fields[i+1]); return result;
    }
    private String read(InputStream input) throws Exception {
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    private JSONArray query(String kind,String date) throws Exception {
        Uri.Builder uri=store.buildUpon().appendPath("query").appendQueryParameter("query",kind);
        if(date!=null)uri.appendQueryParameter("date",date);
        return new JSONArray(read(context.getContentResolver().openInputStream(uri.build())));
    }
    private void mutate(String date,JSONArray changes) throws Exception {
        if(changes.length()>0)context.getContentResolver().call(store,"mutate",obj("date",date,"changes",changes).toString(),null);
    }
    private JSONObject change(String action,long id) throws Exception { return obj("action",action,"id",id); }
    private JSONObject fields(long id,int index,String weight,String reps) throws Exception {
        return obj("action","setFields","id",id,"edits",new JSONArray()
            .put(obj("index",index,"field","value","text",weight)).put(obj("index",index,"field","reps","text",reps)));
    }
    private void fill(String date,String name,double weight,boolean completed) throws Exception {
        JSONArray rows=query("daily",date);
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);if(!name.equals(row.getString("name")))continue;
            long id=row.getLong("id");JSONArray changes=new JSONArray().put(fields(id,0,String.valueOf(weight),"8"))
                .put(change("addSet",id)).put(fields(id,1,String.valueOf(weight+2.5),"8"));
            if(completed)changes.put(change("check",id));mutate(date,changes);return;
        }
        throw new IllegalStateException("Fixture task missing");
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            if(!android.os.Build.PRODUCT.startsWith("sdk_gphone"))throw new IllegalStateException("Test emulator required");
            context=getTargetContext();
            Set<String> demo=new HashSet<>(Arrays.asList("杠铃卧推","高位下拉","罗马尼亚硬拉","哑铃弯举","椭圆仪","平板支撑","侧平板支撑"));
            Set<String> allowed=new HashSet<>(demo);
            JSONArray original=new JSONArray(read(context.getAssets().open("exercises_en.json")));
            for(int i=0;i<original.length();i++)allowed.add(original.getJSONObject(i).getString("name"));
            JSONArray current=query("templates",null);
            if(current.length()>31)throw new IllegalStateException("Non-stock fixture library");
            for(int i=0;i<current.length();i++)if(!allowed.contains(current.getJSONObject(i).getString("name")))
                throw new IllegalStateException("Unexpected personal data; refusing fixture replacement");
            LocalDate today=LocalDate.now();
            for(int d=0;d<=7;d++) {
                String date=today.minusDays(d).toString();JSONArray tasks=query("daily",date),remove=new JSONArray();
                for(int i=0;i<tasks.length();i++)if(demo.contains(tasks.getJSONObject(i).getString("name")))
                    remove.put(change("removeTask",tasks.getJSONObject(i).getLong("id")));
                mutate(date,remove);
            }
            JSONArray changes=new JSONArray();for(int i=0;i<current.length();i++)changes.put(change("deleteTemplate",current.getJSONObject(i).getLong("id")));
            String[][] rows={{"杠铃卧推","0025","STRENGTH","part_chest","equip_barbell","4x8"},
                {"高位下拉","0150","STRENGTH","part_back","equip_cable","3x12"},
                {"罗马尼亚硬拉","0085","STRENGTH","part_thighs","equip_barbell","3x10"},
                {"哑铃弯举","0294","STRENGTH","part_arms","equip_dumbbell","3x12"},
                {"椭圆仪","2141","CARDIO","part_cardio","equip_cardio_machine","25min"},
                {"侧平板支撑","","CORE","part_abs","equip_bodyweight","60s"}};
            JSONObject aliases=new JSONObject(read(context.getAssets().open("catalog/name_matches.json")));
            try(SQLiteDatabase catalog=SQLiteDatabase.openDatabase(new java.io.File(context.getNoBackupFilesDir(),"exercise_catalog/gymnota-catalog-v5.sqlite").getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
                for(String[] row:rows) {
                    String path;
                    if(row[1].isEmpty())path=aliases.getString("侧平板支撑");
                    else try(Cursor cursor=catalog.rawQuery("SELECT gif_path FROM catalog_exercise WHERE source_id=?",new String[]{row[1]})) {
                        if(!cursor.moveToFirst())throw new IllegalStateException("Missing source");path=cursor.getString(0);
                    }
                    String uri="file:///android_asset/catalog/"+path.replaceAll("\\.gif$",".webp");
                    changes.put(obj("action","saveTemplate","id",0,"fields",obj("name",row[0],"target",row[5],"category",row[2],
                        "bodyPart",row[3],"equipment",row[4],"instruction","演示数据：用于展示动作说明与分组记录。",
                        "isUnilateral",row[0].equals("哑铃弯举"),"logType",row[2].equals("STRENGTH")?0:1,"animationUri",uri,"imageUri",JSONObject.NULL)));
                }
            }
            changes.put(obj("action","saveProfile","fields",obj("age",34,"height",178,"gender",0)));
            changes.put(obj("action","patchSettings","field","languageCode","value","zh"));
            changes.put(obj("action","saveSchedule","day",today.getDayOfWeek().getValue(),"type","CORE"));
            mutate(today.toString(),changes);
            Map<String,Long> ids=new HashMap<>();JSONArray templates=query("templates",null);
            for(int i=0;i<templates.length();i++)ids.put(templates.getJSONObject(i).getString("name"),templates.getJSONObject(i).getLong("id"));
            for(int d=7;d>=1;d--) {
                String date=today.minusDays(d).toString();JSONArray items=new JSONArray();
                for(String name:new String[]{"杠铃卧推","高位下拉"})items.put(change("addExercise",ids.get(name)));
                items.put(obj("action","saveWeight","fields",obj("weight",79.8-(7-d)*0.08,"fat",15.1,"muscle",33,"water",58,"waist",84,"hip",98)));
                mutate(date,items);fill(date,"杠铃卧推",50+(7-d)*2,true);fill(date,"高位下拉",35+(7-d),true);
            }
            JSONArray items=new JSONArray();for(String name:new String[]{"杠铃卧推","高位下拉","椭圆仪"})items.put(change("addExercise",ids.get(name)));
            mutate(today.toString(),items);fill(today.toString(),"杠铃卧推",64,false);fill(today.toString(),"高位下拉",42,true);
            result.putString("stream","OK: synthetic README fixture created through Rust transactions\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
    }
}
