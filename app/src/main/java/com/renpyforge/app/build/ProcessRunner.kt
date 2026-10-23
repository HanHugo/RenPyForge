package com.renpyforge.app.build

import java.io.File

/** 外部进程调用封装：统一记录 stdout/stderr 到步骤日志。 */
object ProcessRunner {

    /** 执行命令，exitCode 非零时抛 StepException。返回合并输出。 */
    fun run(step: StepId, log: StringBuilder, vararg command: String, workingDir: File? = null): String {
        val pb = ProcessBuilder(*command).apply {
            redirectErrorStream(true)
            workingDir?.let { directory(it) }
        }
        log.appendLine("\$ ${command.joinToString(" ")}")
        val process = pb.start()
        val output = StringBuilder()
        process.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                output.appendLine(line)
                log.appendLine(line)
            }
        }
        val code = process.waitFor()
        if (code != 0) {
            throw StepException(step, "命令退出码 $code：${command.joinToString(" ")}\n${output.takeLast(2000)}")
        }
        return output.toString()
    }
}
