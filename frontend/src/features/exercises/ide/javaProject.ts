import type { ExerciseFile } from '../../../api/exercises'

/** Tipos que o botão "Novo" cria, como no IntelliJ. */
export type JavaTypeKind = 'class' | 'interface' | 'enum' | 'record' | 'abstract'

export const TYPE_KIND_LABELS: Record<JavaTypeKind, string> = {
  class: 'Classe',
  interface: 'Interface',
  enum: 'Enum',
  record: 'Record',
  abstract: 'Classe abstrata',
}

const DECLARATION: Record<JavaTypeKind, (name: string) => string> = {
  class: (n) => `public class ${n} {\n\n}\n`,
  interface: (n) => `public interface ${n} {\n\n}\n`,
  enum: (n) => `public enum ${n} {\n\n}\n`,
  record: (n) => `public record ${n}() {\n\n}\n`,
  abstract: (n) => `public abstract class ${n} {\n\n}\n`,
}

const JAVA_KEYWORDS = new Set(
  ('abstract assert boolean break byte case catch char class const continue default do double else enum extends final ' +
    'finally float for goto if implements import instanceof int interface long native new package private protected ' +
    'public return short static strictfp super switch synchronized this throw throws transient try void volatile while ' +
    'true false null var record yield sealed permits non-sealed').split(' '),
)

/** Esqueleto do arquivo do tipo, com a linha de package quando houver pacote. */
export function skeleton(kind: JavaTypeKind, name: string, pkg = ''): string {
  const header = pkg ? `package ${pkg};\n\n` : ''
  return header + DECLARATION[kind](name)
}

export function isJavaIdentifier(name: string): boolean {
  return /^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name) && !JAVA_KEYWORDS.has(name)
}

export function isValidPackage(pkg: string): boolean {
  return pkg === '' || pkg.split('.').every(isJavaIdentifier)
}

/** Caminho relativo a src: o pacote vira pastas ("loja.modelo" + "Pedido" → "loja/modelo/Pedido.java"). */
export function pathFor(name: string, pkg = ''): string {
  return (pkg ? pkg.replace(/\./g, '/') + '/' : '') + `${name}.java`
}

export function fileBaseName(path: string): string {
  return path.split('/').pop()!.replace(/\.java$/, '')
}

export function packageOf(path: string): string {
  const parts = path.split('/')
  parts.pop()
  return parts.join('.')
}

/** Nome válido e sem duplicar outro arquivo; devolve a mensagem de erro ou null. */
export function validateTypeName(name: string, pkg: string, files: ExerciseFile[], ignorePath?: string): string | null {
  const trimmed = name.trim()
  if (!trimmed) return 'Informe o nome.'
  if (!isJavaIdentifier(trimmed)) {
    return `"${trimmed}" não é um nome válido em Java (use letras, números, _ ou $, sem começar com número nem usar palavra reservada).`
  }
  if (!isValidPackage(pkg.trim())) return `Pacote inválido: "${pkg.trim()}".`
  const path = pathFor(trimmed, pkg.trim()).toLowerCase()
  if (files.some((f) => f.path.toLowerCase() === path && f.path !== ignorePath)) {
    return `Já existe um arquivo ${pathFor(trimmed, pkg.trim())}.`
  }
  return null
}

const PUBLIC_TYPE = /(public\s+(?:(?:abstract|final|sealed|non-sealed|static|strictfp)\s+)*(?:class|interface|enum|record|@interface)\s+)([A-Za-z_$][A-Za-z0-9_$]*)/
const ANY_TYPE = /(?:class|interface|enum|record)\s+([A-Za-z_$][A-Za-z0-9_$]*)/

/** Nome do arquivo para um código: o tipo público, senão o primeiro tipo, senão Main.java (igual ao backend). */
export function fileNameFor(code: string): string {
  const pub = PUBLIC_TYPE.exec(code)
  if (pub) return `${pub[2]}.java`
  const any = ANY_TYPE.exec(code)
  return any ? `${any[1]}.java` : 'Main.java'
}

/** Renomeia o arquivo e o tipo público dentro dele (o nome do arquivo precisa ser o do tipo público). */
export function renameFile(file: ExerciseFile, newName: string): ExerciseFile {
  const pkgPath = file.path.includes('/') ? file.path.slice(0, file.path.lastIndexOf('/') + 1) : ''
  const oldName = fileBaseName(file.path)
  const content = file.content.replace(PUBLIC_TYPE, (match, prefix: string, name: string) =>
    name === oldName ? prefix + newName : match,
  )
  return { path: `${pkgPath}${newName}.java`, content }
}

export function hasMainMethod(content: string): boolean {
  return /(public\s+static|static\s+public)\s+void\s+main\s*\(\s*(final\s+)?String\s*(\[\s*\]|\.\.\.)/.test(content)
}

/** Arquivo com o main (Main.java tem preferência); null quando nenhum tem. */
export function mainFilePath(files: ExerciseFile[]): string | null {
  const withMain = files.filter((f) => hasMainMethod(f.content))
  return (withMain.find((f) => fileBaseName(f.path) === 'Main') ?? withMain[0])?.path ?? null
}

const DEFAULT_MAIN = 'public class Main {\n    public static void main(String[] args) {\n        \n    }\n}\n'

/** Projeto inicial do exercício: os arquivos gerados ou o starterCode como arquivo único. */
export function initialFiles(exercise: { starterFiles?: ExerciseFile[] | null; starterCode?: string | null }): ExerciseFile[] {
  if (exercise.starterFiles && exercise.starterFiles.length > 0) {
    return exercise.starterFiles.map((f) => ({ ...f }))
  }
  const code = exercise.starterCode?.trim() ? exercise.starterCode : DEFAULT_MAIN
  return [{ path: fileNameFor(code), content: code }]
}

/** Texto único com todos os arquivos, para a tutora (mesmo formato do backend). */
export function filesToText(files: ExerciseFile[]): string {
  if (files.length === 1) return files[0].content
  return files.map((f) => `// Arquivo: ${f.path}\n${f.content}`).join('\n\n')
}

/** Linhas "at pacote.Classe.metodo(Arquivo.java:12)" de uma exceção → arquivo do projeto e linha. */
export function stackLocations(text: string, files: ExerciseFile[]): { path: string; line: number; index: number; length: number }[] {
  const result: { path: string; line: number; index: number; length: number }[] = []
  const re = /at\s+([\w$.]+)\.[\w$<>]+\(([\w$]+\.java):(\d+)\)/g
  let m: RegExpExecArray | null
  while ((m = re.exec(text)) !== null) {
    const qualified = m[1].replace(/\$.*$/, '')
    const pkg = qualified.includes('.') ? qualified.slice(0, qualified.lastIndexOf('.')) : ''
    const path = pathFor(m[2].replace(/\.java$/, ''), pkg)
    if (files.some((f) => f.path === path)) {
      const location = `${m[2]}:${m[3]}`
      result.push({ path, line: Number(m[3]), index: m.index + m[0].indexOf(location), length: location.length })
    }
  }
  return result
}

/** Snippets do IntelliJ (sintaxe de snippet do Monaco: $0 é o cursor final). */
export const JAVA_SNIPPETS: { label: string; detail: string; body: string }[] = [
  { label: 'psvm', detail: 'public static void main(String[] args)', body: 'public static void main(String[] args) {\n\t$0\n}' },
  { label: 'sout', detail: 'System.out.println()', body: 'System.out.println($0);' },
  { label: 'fori', detail: 'for (int i = 0; i < n; i++)', body: 'for (int ${1:i} = 0; ${1:i} < ${2:n}; ${1:i}++) {\n\t$0\n}' },
  { label: 'iter', detail: 'for (Tipo item : itens)', body: 'for (${1:var} ${2:item} : ${3:itens}) {\n\t$0\n}' },
]

/** Exercícios com resposta em código Java usam o editor; SQL e análise continuam com resposta em texto. */
export function isJavaProjectExercise(ex: { language?: string | null; exerciseType?: string; starterCode?: string | null }): boolean {
  if (ex.exerciseType === 'SQL_QUERY' || ex.exerciseType === 'CODE_ANALYSIS') return false
  if (ex.language === 'JAVA') return true
  return !ex.language && /\b(class|interface|enum|record)\s+\w+/.test(ex.starterCode ?? '')
}
