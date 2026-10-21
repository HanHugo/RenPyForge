# RenPyForge 启动壳：把 Python 的 stdout/stderr/异常写到 ANDROID_PUBLIC（外部目录），
# 便于在构建器终端中诊断引擎启动失败。
import os
import sys
import types

_log = None
try:
    _path = os.path.join(os.environ.get("ANDROID_PUBLIC") or os.getcwd(), "python-log.txt")
    _log = open(_path, "w", buffering=1)
    sys.stdout = _log
    sys.stderr = _log

    def _excepthook(t, v, tb):
        import traceback
        traceback.print_exception(t, v, tb, file=_log)
        _log.flush()

    sys.excepthook = _excepthook
    print("MARK: python shim v3 (module-__main__) start, log at", _path)
    try:
        print("cwd:", os.getcwd())
        print("lib/python3.12/zipfile:", sorted(os.listdir("lib/python3.12/zipfile")))
        print("lib/python3.12/zipfile/_path:", sorted(os.listdir("lib/python3.12/zipfile/_path")))
    except Exception as e:
        print("recon failed:", e)
except Exception as e:
    try:
        print("shim log setup failed:", e)
    except Exception:
        pass

# renpy.py 会读取 sys.modules[__name__] 并把 renpy.__main__ 指向它
# （renpy.bootstrap 依赖其中的 path_to_gamedir 等），因此必须让
# exec 的命名空间成为一个真正注册在 sys.modules["__main__"] 的模块对象。
_main_mod = types.ModuleType("__main__")
_main_mod.__file__ = "renpy-main.py"
sys.modules["__main__"] = _main_mod

_g = _main_mod.__dict__
try:
    exec(compile(open("renpy-main.py").read(), "renpy-main.py", "exec"), _g)
except SystemExit as e:
    print("MARK: renpy-main.py SystemExit: %r" % (e,))
    raise
except BaseException:
    import traceback
    traceback.print_exc()
    print("MARK: renpy-main.py raised (see traceback above)")
    raise
finally:
    try:
        print("MARK: python shim end")
        _log.flush()
    except Exception:
        pass
