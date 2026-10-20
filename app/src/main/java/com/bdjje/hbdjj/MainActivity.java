import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('ar', null);
  runApp(const MyNotesApp());
}

class MyNotesApp extends StatelessWidget {
  const MyNotesApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'مذكرتي',
      debugShowCheckedModeBanner: false,
      locale: const Locale('ar'),
      supportedLocales: const [Locale('ar')],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      theme: ThemeData(
        brightness: Brightness.dark,
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF3B82F6),
          brightness: Brightness.dark,
        ),
        scaffoldBackgroundColor: const Color(0xFF0F172A),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFF1E293B),
          foregroundColor: Colors.white,
          elevation: 0,
          centerTitle: true,
        ),
        floatingActionButtonTheme: const FloatingActionButtonThemeData(
          backgroundColor: Color(0xFF3B82F6),
          foregroundColor: Colors.white,
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: const Color(0xFF1E293B),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: BorderSide.none,
          ),
          hintStyle: const TextStyle(color: Color(0xFF94A3B8)),
        ),
        cardTheme: CardThemeData(
          color: const Color(0xFF1E293B),
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
        dialogTheme: const DialogThemeData(
          backgroundColor: Color(0xFF1E293B),
        ),
      ),
      home: const NotesHomePage(),
    );
  }
}

class Note {
  final String id;
  final String title;
  final String body;
  final DateTime createdAt;

  Note({
    required this.id,
    required this.title,
    required this.body,
    required this.createdAt,
  });

  Map<String, dynamic> toMap() => {
        'id': id,
        'title': title,
        'body': body,
        'createdAt': createdAt.toIso8601String(),
      };

  factory Note.fromMap(Map<String, dynamic> map) => Note(
        id: (map['id'] ?? '') as String,
        title: (map['title'] ?? '') as String,
        body: (map['body'] ?? '') as String,
        createdAt: DateTime.tryParse(map['createdAt'] as String? ?? '') ??
            DateTime.now(),
      );
}

class NotesRepository {
  static const String _storageKey = 'my_notes_v1';

  Future<List<Note>> loadNotes() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_storageKey);
    if (raw == null || raw.isEmpty) return [];
    try {
      final decoded = jsonDecode(raw) as List<dynamic>;
      return decoded
          .map((item) => Note.fromMap(item as Map<String, dynamic>))
          .toList();
    } catch (_) {
      return [];
    }
  }

  Future<void> saveNotes(List<Note> notes) async {
    final prefs = await SharedPreferences.getInstance();
    final raw = jsonEncode(notes.map((n) => n.toMap()).toList());
    await prefs.setString(_storageKey, raw);
  }
}

class NotesHomePage extends StatefulWidget {
  const NotesHomePage({super.key});

  @override
  State<NotesHomePage> createState() => _NotesHomePageState();
}

class _NotesHomePageState extends State<NotesHomePage> {
  final NotesRepository _repository = NotesRepository();
  final TextEditingController _searchController = TextEditingController();
  List<Note> _notes = [];
  String _query = '';
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final notes = await _repository.loadNotes();
    notes.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    if (!mounted) return;
    setState(() {
      _notes = notes;
      _loading = false;
    });
  }

  List<Note> get _filtered {
    if (_query.trim().isEmpty) return _notes;
    final q = _query.trim().toLowerCase();
    return _notes.where((n) {
      return n.title.toLowerCase().contains(q) ||
          n.body.toLowerCase().contains(q);
    }).toList();
  }

  Future<void> _persist() async {
    await _repository.saveNotes(_notes);
  }

  void _openEditor({Note? existing}) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => NoteEditorPage(
          initialNote: existing,
          onSave: (title, body) async {
            if (existing == null) {
              _notes.add(Note(
                id: DateTime.now().microsecondsSinceEpoch.toString(),
                title: title,
                body: body,
                createdAt: DateTime.now(),
              ));
            } else {
              final index = _notes.indexWhere((n) => n.id == existing.id);
              if (index != -1) {
                _notes[index] = Note(
                  id: existing.id,
                  title: title,
                  body: body,
                  createdAt: existing.createdAt,
                );
              }
            }
            await _persist();
            if (!mounted) return;
            setState(() {});
          },
        ),
      ),
    );
  }

  Future<void> _confirmDelete(Note note) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('حذف الملاحظة'),
        content: const Text('هل أنت متأكد من حذف هذه الملاحظة؟'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('إلغاء'),
          ),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('حذف', style: TextStyle(color: Colors.redAccent)),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      _notes.removeWhere((n) => n.id == note.id);
      await _persist();
      if (!mounted) return;
      setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('مذكرتي'),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                  child: TextField(
                    controller: _searchController,
                    textDirection: TextDirection.rtl,
                    decoration: const InputDecoration(
                      hintText: 'بحث في الملاحظات...',
                      prefixIcon: Icon(Icons.search),
                    ),
                    onChanged: (value) {
                      setState(() => _query = value);
                    },
                  ),
                ),
                Expanded(
                  child: _filtered.isEmpty
                      ? Center(
                          child: Text(
                            _query.isEmpty
                                ? 'لا توجد ملاحظات بعد'
                                : 'لا توجد نتائج مطابقة',
                            style: const TextStyle(
                              color: Color(0xFF94A3B8),
                              fontSize: 16,
                            ),
                          ),
                        )
                      : ListView.builder(
                          padding: const EdgeInsets.all(16),
                          itemCount: _filtered.length,
                          itemBuilder: (_, index) {
                            final note = _filtered[index];
                            return Padding(
                              padding: const EdgeInsets.only(bottom: 12),
                              child: Card(
                                child: ListTile(
                                  contentPadding: const EdgeInsets.symmetric(
                                    horizontal: 16,
                                    vertical: 8,
                                  ),
                                  title: Text(
                                    note.title.isEmpty
                                        ? 'بدون عنوان'
                                        : note.title,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                      fontSize: 16,
                                    ),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                  subtitle: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      if (note.body.isNotEmpty)
                                        Padding(
                                          padding:
                                              const EdgeInsets.only(top: 6),
                                          child: Text(
                                            note.body,
                                            maxLines: 2,
                                            overflow: TextOverflow.ellipsis,
                                            style: const TextStyle(
                                              color: Color(0xFFCBD5E1),
                                            ),
                                          ),
                                        ),
                                      Padding(
                                        padding: const EdgeInsets.only(top: 8),
                                        child: Text(
                                          DateFormat(
                                                  'yyyy/MM/dd – HH:mm', 'ar')
                                              .format(note.createdAt),
                                          style: const TextStyle(
                                            fontSize: 12,
                                            color: Color(0xFF64748B),
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                  onTap: () => _openEditor(existing: note),
                                  trailing: IconButton(
                                    icon: const Icon(Icons.delete_outline,
                                        color: Colors.redAccent),
                                    onPressed: () => _confirmDelete(note),
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                ),
              ],
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _openEditor(),
        child: const Icon(Icons.add),
      ),
    );
  }
}

class NoteEditorPage extends StatefulWidget {
  final Note? initialNote;
  final Future<void> Function(String title, String body) onSave;

  const NoteEditorPage({
    super.key,
    this.initialNote,
    required this.onSave,
  });

  @override
  State<NoteEditorPage> createState() => _NoteEditorPageState();
}

class _NoteEditorPageState extends State<NoteEditorPage> {
  late final TextEditingController _titleController;
  late final TextEditingController _bodyController;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _titleController =
        TextEditingController(text: widget.initialNote?.title ?? '');
    _bodyController =
        TextEditingController(text: widget.initialNote?.body ?? '');
  }

  @override
  void dispose() {
    _titleController.dispose();
    _bodyController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final title = _titleController.text.trim();
    final body = _bodyController.text.trim();

    if (title.isEmpty && body.isEmpty) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('لا يمكن حفظ ملاحظة فارغة')),
      );
      return;
    }

    setState(() => _saving = true);
    try {
      await widget.onSave(title, body);
      if (!mounted) return;
      Navigator.of(context).pop();
    } catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('حدث خطأ أثناء الحفظ')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title:
            Text(widget.initialNote == null ? 'ملاحظة جديدة' : 'تعديل الملاحظة'),
        actions: [
          IconButton(
            onPressed: _saving ? null : _save,
            icon: _saving
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.save_outlined),
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              controller: _titleController,
              textDirection: TextDirection.rtl,
              decoration: const InputDecoration(
                hintText: 'العنوان',
              ),
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 12),
            Expanded(
              child: TextField(
                controller: _bodyController,
                textDirection: TextDirection.rtl,
                maxLines: null,
                expands: true,
                textAlignVertical: TextAlignVertical.top,
                decoration: const InputDecoration(
                  hintText: 'اكتب ملاحظتك هنا...',
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}package com.bdjje.hbdjj;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * لوحة رسم بسيطة — قالب KlencodIDE
 *
 * الميزات:
 *   - رسم بالإصبع (Canvas + Path)
 *   - 8 ألوان
 *   - 3 سماكات فرشاة
 *   - تراجع (Undo)
 *   - مسح الكل
 *   - حفظ PNG في Pictures/KlencodDrawings (Android 10+)
 *
 * © 2026 KlencodIDE
 */
public class MainActivity extends Activity {

    // ============================================================
    // الألوان الأساسية (نفس ألوان KlencodIDE)
    // ============================================================
    private static final int COLOR_BG = 0xFF0D1117;
    private static final int COLOR_SURFACE = 0xFF161B22;
    private static final int COLOR_SURFACE_ALT = 0xFF21262D;
    private static final int COLOR_PRIMARY = 0xFF1F6FEB;
    private static final int COLOR_PRIMARY_LIGHT = 0xFF58A6FF;
    private static final int COLOR_TEXT = 0xFFE6EDF3;
    private static final int COLOR_MUTED = 0xFF8B949E;
    private static final int COLOR_BORDER = 0xFF30363D;

    // 8 ألوان للرسم
    private static final int[] PALETTE = {
            0xFFFFFFFF, // أبيض
            0xFF000000, // أسود
            0xFFFF7B72, // أحمر
            0xFF7EE787, // أخضر
            0xFF58A6FF, // أزرق
            0xFFD2A8FF, // بنفسجي
            0xFFFFD33D, // أصفر
            0xFFFF9E64  // برتقالي
    };

    // 3 سماكات
    private static final float[] BRUSH_SIZES = {4f, 10f, 22f};

    // ============================================================
    // الحالة
    // ============================================================
    private DrawingView drawingView;
    private int currentColorIndex = 2;  // أحمر افتراضي
    private int currentSizeIndex = 1;   // متوسط افتراضي
    private final ArrayList<Button> colorButtons = new ArrayList<>();
    private final ArrayList<Button> sizeButtons = new ArrayList<>();

    // ============================================================
    // dp helper
    // ============================================================
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ============================================================
    // onCreate
    // ============================================================
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);

        // Root
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);

        // ============================================================
        // الترويسة العلوية
        // ============================================================
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(10));
        header.setBackgroundColor(COLOR_SURFACE);

        TextView title = new TextView(this);
        title.setText("🎨 لوحة الرسم");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(17);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        Button undoBtn = makeTopButton("↶ تراجع");
        undoBtn.setOnClickListener(v -> drawingView.undo());
        header.addView(undoBtn, new LinearLayout.LayoutParams(-2, dp(40)));

        Button clearBtn = makeTopButton("🗑 مسح");
        clearBtn.setOnClickListener(v -> confirmClear());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(-2, dp(40));
        clearParams.leftMargin = dp(6);
        header.addView(clearBtn, clearParams);

        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // ============================================================
        // منطقة الرسم
        // ============================================================
        FrameLayout drawContainer = new FrameLayout(this);
        drawContainer.setBackgroundColor(Color.WHITE);

        drawingView = new DrawingView(this);
        drawContainer.addView(drawingView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams drawParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        drawParams.setMargins(dp(12), dp(12), dp(12), dp(12));
        root.addView(drawContainer, drawParams);

        // ============================================================
        // شريط التحكم السفلي
        // ============================================================
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(10), dp(12), dp(12));
        controls.setBackgroundColor(COLOR_SURFACE);

        // --- صف الألوان ---
        TextView colorLabel = new TextView(this);
        colorLabel.setText("اللون:");
        colorLabel.setTextColor(COLOR_MUTED);
        colorLabel.setTextSize(12);
        controls.addView(colorLabel);

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams colorRowParams = new LinearLayout.LayoutParams(-1, dp(50));
        colorRowParams.topMargin = dp(6);
        controls.addView(colorRow, colorRowParams);

        for (int i = 0; i < PALETTE.length; i++) {
            final int index = i;
            Button colorBtn = new Button(this);
            colorBtn.setBackground(makeCircleDrawable(PALETTE[i]));
            colorBtn.setPadding(0, 0, 0, 0);
            colorBtn.setOnClickListener(v -> selectColor(index));

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            cp.setMargins(dp(3), 0, dp(3), 0);
            colorRow.addView(colorBtn, cp);
            colorButtons.add(colorBtn);
        }

        // --- صف السماكات ---
        TextView sizeLabel = new TextView(this);
        sizeLabel.setText("سماكة الفرشاة:");
        sizeLabel.setTextColor(COLOR_MUTED);
        sizeLabel.setTextSize(12);
        LinearLayout.LayoutParams sizeLabelParams = new LinearLayout.LayoutParams(-1, -2);
        sizeLabelParams.topMargin = dp(12);
        controls.addView(sizeLabel, sizeLabelParams);

        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sizeRowParams = new LinearLayout.LayoutParams(-1, dp(46));
        sizeRowParams.topMargin = dp(6);
        controls.addView(sizeRow, sizeRowParams);

        String[] sizeNames = {"رفيع", "متوسط", "عريض"};
        for (int i = 0; i < BRUSH_SIZES.length; i++) {
            final int index = i;
            Button sizeBtn = new Button(this);
            sizeBtn.setText(sizeNames[i]);
            sizeBtn.setTextSize(12);
            sizeBtn.setAllCaps(false);
            sizeBtn.setPadding(0, 0, 0, 0);
            sizeBtn.setOnClickListener(v -> selectSize(index));

            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            sp.setMargins(dp(4), 0, dp(4), 0);
            sizeRow.addView(sizeBtn, sp);
            sizeButtons.add(sizeBtn);
        }

        // --- صف الحفظ ---
        Button saveBtn = new Button(this);
        saveBtn.setText("💾 حفظ الصورة");
        saveBtn.setTextSize(14);
        saveBtn.setAllCaps(false);
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(0xFF238636);
        saveBg.setCornerRadius(dp(12));
        saveBg.setStroke(dp(1), 0xFF56D364);
        saveBtn.setBackground(saveBg);
        saveBtn.setElevation(dp(2));
        saveBtn.setOnClickListener(v -> saveImage());

        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(50));
        saveParams.topMargin = dp(14);
        controls.addView(saveBtn, saveParams);

        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);

        // الاختيارات الافتراضية
        selectColor(currentColorIndex);
        selectSize(currentSizeIndex);
    }

    // ============================================================
    // دوال مساعدة للواجهة
    // ============================================================
    private Button makeTopButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(12);
        btn.setAllCaps(false);
        btn.setTextColor(COLOR_TEXT);
        btn.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_SURFACE_ALT);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), COLOR_BORDER);
        btn.setBackground(bg);
        return btn;
    }

    private GradientDrawable makeCircleDrawable(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setStroke(dp(2), COLOR_BORDER);
        return d;
    }

    private GradientDrawable makeSelectedCircleDrawable(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setStroke(dp(3), COLOR_PRIMARY_LIGHT);
        return d;
    }

    private GradientDrawable makeSizeButtonDrawable(boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(10));
        d.setColor(selected ? COLOR_PRIMARY : COLOR_SURFACE_ALT);
        d.setStroke(dp(1), selected ? COLOR_PRIMARY_LIGHT : COLOR_BORDER);
        return d;
    }

    private void selectColor(int index) {
        currentColorIndex = index;
        drawingView.setColor(PALETTE[index]);

        for (int i = 0; i < colorButtons.size(); i++) {
            Button b = colorButtons.get(i);
            if (i == index) {
                b.setBackground(makeSelectedCircleDrawable(PALETTE[i]));
            } else {
                b.setBackground(makeCircleDrawable(PALETTE[i]));
            }
        }
    }

    private void selectSize(int index) {
        currentSizeIndex = index;
        drawingView.setStrokeWidth(BRUSH_SIZES[index]);

        for (int i = 0; i < sizeButtons.size(); i++) {
            Button b = sizeButtons.get(i);
            b.setBackground(makeSizeButtonDrawable(i == index));
            b.setTextColor(i == index ? Color.WHITE : COLOR_TEXT);
        }
    }

    // ============================================================
    // حفظ الصورة
    // ============================================================
    private void saveImage() {
        try {
            Bitmap bitmap = drawingView.exportBitmap();
            if (bitmap == null) {
                Toast.makeText(this, "لا يوجد شيء لحفظه", Toast.LENGTH_SHORT).show();
                return;
            }

            String timestamp = new SimpleDateFormat(
                    "yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "drawing_" + timestamp + ".png";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/KlencodDrawings");

                Uri uri = getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                if (uri == null) {
                    Toast.makeText(this, "تعذر إنشاء ملف", Toast.LENGTH_SHORT).show();
                    return;
                }

                OutputStream out = getContentResolver().openOutputStream(uri);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                if (out != null) out.close();
                bitmap.recycle();

                Toast.makeText(this,
                        "✅ تم حفظ الصورة في:\nPictures/KlencodDrawings/" + fileName,
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this,
                        "يحتاج Android 10+ للحفظ",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "خطأ: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("مسح اللوحة")
                .setMessage("هل أنت متأكد من مسح كل الرسم؟")
                .setPositiveButton("مسح", (d, w) -> drawingView.clear())
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // ============================================================
    // DrawingView — Canvas مخصص
    // ============================================================
    private static class DrawingView extends View {

        private final ArrayList<Stroke> strokes = new ArrayList<>();
        private Stroke currentStroke;

        private int currentColor = Color.RED;
        private float currentStrokeWidth = 10f;

        public DrawingView(Activity context) {
            super(context);
            setFocusable(true);
            setBackgroundColor(Color.WHITE);
        }

        public void setColor(int color) {
            this.currentColor = color;
        }

        public void setStrokeWidth(float width) {
            this.currentStrokeWidth = width;
        }

        public void clear() {
            strokes.clear();
            currentStroke = null;
            invalidate();
        }

        public void undo() {
            if (strokes.isEmpty()) return;
            strokes.remove(strokes.size() - 1);
            invalidate();
        }

        public Bitmap exportBitmap() {
            if (getWidth() <= 0 || getHeight() <= 0) return null;
            Bitmap bmp = Bitmap.createBitmap(
                    getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            canvas.drawColor(Color.WHITE);

            for (Stroke stroke : strokes) {
                stroke.draw(canvas);
            }
            if (currentStroke != null) {
                currentStroke.draw(canvas);
            }
            return bmp;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            for (Stroke stroke : strokes) {
                stroke.draw(canvas);
            }

            if (currentStroke != null) {
                currentStroke.draw(canvas);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    currentStroke = new Stroke(currentColor, currentStrokeWidth);
                    currentStroke.addPoint(x, y);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (currentStroke != null) {
                        currentStroke.addPoint(x, y);
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (currentStroke != null) {
                        strokes.add(currentStroke);
                        currentStroke = null;
                        invalidate();
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    // ============================================================
    // Stroke — ضربة واحدة (مسار واحد)
    // ============================================================
    private static class Stroke {

        private final Paint paint;
        private final Path path;

        public Stroke(int color, float strokeWidth) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setDither(true);

            path = new Path();
        }

        public void addPoint(float x, float y) {
            if (path.isEmpty()) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }

        public void draw(Canvas canvas) {
            canvas.drawPath(path, paint);
        }
    }
}