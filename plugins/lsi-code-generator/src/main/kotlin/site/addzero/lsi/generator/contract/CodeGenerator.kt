package site.addzero.lsi.generator.contract

interface CodeGenerator<TInput, TOutput> {
    fun support(input: TInput): Boolean
    fun generate(input: TInput): TOutput
}

interface BatchCodeGenerator<TInput, TOutput> : CodeGenerator<TInput, TOutput> {
    fun generateBatch(inputs: List<TInput>): List<TOutput> = inputs.filter { support(it) }.map { generate(it) }
}
