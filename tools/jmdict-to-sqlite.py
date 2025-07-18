import json
import sqlite3
import sys
from typing import Dict, List, Any


class DictionaryConverter:
    def __init__(self, db_path: str):
        self.conn = sqlite3.connect(db_path)
        self.conn.execute("PRAGMA foreign_keys = ON;")
        self.create_tables()

    def create_tables(self):
        self.conn.executescript("""
            CREATE TABLE IF NOT EXISTS dictionary_info (
                id INTEGER PRIMARY KEY,
                version TEXT,
                languages TEXT,
                common_only INTEGER,
                dict_date TEXT,
                dict_revisions TEXT
            );

            CREATE TABLE IF NOT EXISTS tags (
                key TEXT PRIMARY KEY,
                description TEXT
            );

            CREATE TABLE IF NOT EXISTS words (
                id TEXT PRIMARY KEY
            );

            CREATE TABLE IF NOT EXISTS kanji (
                kanji_id INTEGER PRIMARY KEY AUTOINCREMENT,
                word_id TEXT NOT NULL,
                common INTEGER,
                text TEXT NOT NULL,
                tags TEXT NOT NULL,
                FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS kana (
                kana_id INTEGER PRIMARY KEY AUTOINCREMENT,
                word_id TEXT NOT NULL,
                common INTEGER,
                text TEXT NOT NULL,
                tags TEXT NOT NULL,
                applies_to_kanji TEXT NOT NULL,
                FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS sense (
                sense_id INTEGER PRIMARY KEY AUTOINCREMENT,
                word_id TEXT NOT NULL,
                part_of_speech TEXT NOT NULL,
                applies_to_kanji TEXT NOT NULL,
                applies_to_kana TEXT NOT NULL,
                related TEXT NOT NULL,
                antonym TEXT NOT NULL,
                field TEXT NOT NULL,
                dialect TEXT NOT NULL,
                misc TEXT NOT NULL,
                info TEXT NOT NULL,
                language_source TEXT NOT NULL,
                FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS gloss (
                gloss_id INTEGER PRIMARY KEY AUTOINCREMENT,
                sense_id INTEGER NOT NULL,
                lang TEXT NOT NULL,
                gender TEXT,
                type TEXT,
                text TEXT NOT NULL,
                FOREIGN KEY (sense_id) REFERENCES sense(sense_id) ON DELETE CASCADE
            );

            CREATE INDEX IF NOT EXISTS idx_words_id ON words(id);
            CREATE INDEX IF NOT EXISTS idx_kanji_word_id ON kanji(word_id);
            CREATE INDEX IF NOT EXISTS idx_kana_word_id ON kana(word_id);
            CREATE INDEX IF NOT EXISTS idx_sense_word_id ON sense(word_id);
            CREATE INDEX IF NOT EXISTS idx_gloss_sense_id ON gloss(sense_id);
        """)

    def convert_json_file(self, json_file_path: str):
        try:
            with open(json_file_path, 'r', encoding='utf-8') as f:
                data = json.load(f)

            self.conn.execute("BEGIN")  # Start transaction

            self.insert_dictionary_info(data)
            self.insert_tags(data.get('tags', {}))
            self.insert_words_normalized(data.get('words', []))

            self.conn.commit()
            print(f"Successfully converted {len(data.get('words', []))} words to normalized SQLite.")

        except Exception as e:
            self.conn.rollback()
            print(f"Error converting JSON: {e}")
        finally:
            self.conn.close()

    def insert_dictionary_info(self, data: Dict[str, Any]):
        self.conn.execute("""
            INSERT OR REPLACE INTO dictionary_info 
            (id, version, languages, common_only, dict_date, dict_revisions)
            VALUES (1, ?, ?, ?, ?, ?)
        """, (
            data.get('version'),
            json.dumps(data.get('languages', [])),
            data.get('commonOnly', False),
            data.get('dictDate'),
            json.dumps(data.get('dictRevisions', []))
        ))

    def insert_tags(self, tags: Dict[str, str]):
        tag_data = [(key, desc) for key, desc in tags.items()]
        self.conn.executemany("""
            INSERT OR REPLACE INTO tags (key, description) VALUES (?, ?)
        """, tag_data)

    def insert_words_normalized(self, words: List[Dict[str, Any]]):
        for word in words:
            word_id = str(word['id'])
            self.conn.execute("INSERT INTO words (id) VALUES (?)", (word_id,))

            # Insert kanji
            for k in word.get('kanji', []):
                self.conn.execute("""
                    INSERT INTO kanji (word_id, common, text, tags)
                    VALUES (?, ?, ?, ?)
                """, (
                    word_id,
                    int(k.get('common', False)),
                    k['text'],
                    json.dumps(k.get('tags', []))
                ))

            # Insert kana
            for k in word.get('kana', []):
                self.conn.execute("""
                    INSERT INTO kana (word_id, common, text, tags, applies_to_kanji)
                    VALUES (?, ?, ?, ?, ?)
                """, (
                    word_id,
                    int(k.get('common', False)),
                    k['text'],
                    json.dumps(k.get('tags', [])),
                    json.dumps(k.get('appliesToKanji', []))
                ))

            # Insert sense + gloss
            for s in word.get('sense', []):
                cursor = self.conn.execute("""
                    INSERT INTO sense (
                        word_id, part_of_speech, applies_to_kanji, applies_to_kana,
                        related, antonym, field, dialect, misc, info, language_source
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    word_id,
                    json.dumps(s.get('partOfSpeech', [])),
                    json.dumps(s.get('appliesToKanji', [])),
                    json.dumps(s.get('appliesToKana', [])),
                    json.dumps(s.get('related', [])),
                    json.dumps(s.get('antonym', [])),
                    json.dumps(s.get('field', [])),
                    json.dumps(s.get('dialect', [])),
                    json.dumps(s.get('misc', [])),
                    json.dumps(s.get('info', [])),
                    json.dumps(s.get('languageSource', []))
                ))
                sense_id = cursor.lastrowid

                for g in s.get('gloss', []):
                    self.conn.execute("""
                        INSERT INTO gloss (sense_id, lang, gender, type, text)
                        VALUES (?, ?, ?, ?, ?)
                    """, (
                        sense_id,
                        g.get('lang'),
                        g.get('gender'),
                        g.get('type'),
                        g.get('text')
                    ))

    def close(self):
        self.conn.close()


def main():
    if len(sys.argv) != 3:
        print("Usage: python converter.py <input.json> <output.db>")
        sys.exit(1)

    json_file = sys.argv[1]
    db_file = sys.argv[2]

    converter = DictionaryConverter(db_file)
    converter.convert_json_file(json_file)

if __name__ == "__main__":
    main()
