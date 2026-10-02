import importlib.util
import io
from pathlib import Path
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("public_repository", Path(__file__).resolve().parents[1] / "check-public-repository.py")
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class PublicRepositoryTest(unittest.TestCase):
    def test_environment_example_is_allowed(self):
        self.assertEqual([], guard.inspect(".env.example", b"API_TOKEN=\n"))
        self.assertEqual([], guard.inspect("deploy/hermes-site.env.example", b"MAIMAI_SITE_PROVIDER_KEY=\n"))

    def test_named_environment_files_and_client_secret_bindings_are_blocked(self):
        self.assertIn("private-configuration", guard.inspect("deploy/hermes-site.env", b""))
        variable = "VITE_" + "MODEL_API_KEY"
        self.assertIn("frontend-secret-binding", guard.inspect("frontend/src/config.ts", ("import.meta.env."+variable).encode()))

    def test_private_directory_and_environment_are_blocked(self):
        self.assertIn("private-directory", guard.inspect(".local/private/runtime.json", b"{}"))
        self.assertIn("private-configuration", guard.inspect("frontend/.env.production", b""))

    def test_archives_and_personal_documents_are_blocked(self):
        self.assertIn("private-export-or-key", guard.inspect("release.zip", b""))
        self.assertIn("personal-course-document", guard.inspect("docs/course/report.docx", b""))

    def test_generated_secret_patterns_are_detected_without_echoing_value(self):
        token = "gh" + "p_" + "x" * 30
        self.assertEqual(["provider-token"], guard.inspect("config.txt", token.encode()))

    def test_word_runs_are_joined_before_scanning(self):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as archive:
            archive.writestr("word/document.xml", "<p><t>学号：</t><t>1234</t><t>567890</t></p>")
        allowed = next(iter(guard.PUBLIC_DOCUMENTS))
        self.assertIn("student-id", guard.inspect(allowed, buffer.getvalue()))

    def test_schema_and_public_fixture_do_not_trigger(self):
        self.assertEqual([], guard.inspect("backend/src/main/resources/db/migration/V1__identity.sql", b"CREATE TABLE users (id BIGINT);"))
        self.assertEqual([], guard.inspect("tests/fixture.yml", b"password: ci-fixture-only"))


if __name__ == "__main__":
    unittest.main()
