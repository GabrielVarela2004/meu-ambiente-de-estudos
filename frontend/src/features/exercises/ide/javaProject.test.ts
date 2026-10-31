import { describe, expect, it } from 'vitest'
import {
  fileNameFor,
  filesToText,
  initialFiles,
  isJavaProjectExercise,
  mainFilePath,
  pathFor,
  renameFile,
  skeleton,
  stackLocations,
  validateTypeName,
} from './javaProject'

describe('javaProject', () => {
  it('cria o esqueleto certo para Classe, Interface, Enum, Record e Classe abstrata', () => {
    expect(skeleton('class', 'Pedido')).toBe('public class Pedido {\n\n}\n')
    expect(skeleton('interface', 'Forma')).toBe('public interface Forma {\n\n}\n')
    expect(skeleton('enum', 'Cor')).toBe('public enum Cor {\n\n}\n')
    expect(skeleton('record', 'Ponto')).toBe('public record Ponto() {\n\n}\n')
    expect(skeleton('abstract', 'Animal')).toBe('public abstract class Animal {\n\n}\n')
    expect(skeleton('enum', 'Status', 'loja.modelo')).toBe('package loja.modelo;\n\npublic enum Status {\n\n}\n')
    expect(pathFor('Status', 'loja.modelo')).toBe('loja/modelo/Status.java')
  })

  it('valida o nome: identificador Java, sem palavra reservada, pacote válido e sem duplicar', () => {
    const files = [{ path: 'Main.java', content: '' }, { path: 'loja/Pedido.java', content: '' }]
    expect(validateTypeName('Cor', '', files)).toBeNull()
    expect(validateTypeName('', '', files)).toMatch(/Informe o nome/)
    expect(validateTypeName('1Cor', '', files)).toMatch(/não é um nome válido/)
    expect(validateTypeName('class', '', files)).toMatch(/não é um nome válido/)
    expect(validateTypeName('Minha Classe', '', files)).toMatch(/não é um nome válido/)
    expect(validateTypeName('Cor', 'loja.1x', files)).toMatch(/Pacote inválido/)
    expect(validateTypeName('main', '', files)).toMatch(/Já existe/)
    expect(validateTypeName('Pedido', 'loja', files)).toMatch(/Já existe/)
    expect(validateTypeName('Pedido', 'loja', files, 'loja/Pedido.java')).toBeNull()
  })

  it('renomeia o arquivo e o tipo público junto (nome do arquivo = tipo público)', () => {
    const renamed = renameFile({ path: 'loja/Pedido.java', content: 'package loja;\npublic final class Pedido {\n  Pedido() {}\n}' }, 'Compra')
    expect(renamed.path).toBe('loja/Compra.java')
    expect(renamed.content).toContain('public final class Compra {')
  })

  it('encontra o main (Main.java tem preferência) e monta o projeto inicial', () => {
    const files = [
      { path: 'App.java', content: 'public class App { public static void main(String[] args) {} }' },
      { path: 'Main.java', content: 'public class Main { public static void main(String... a) {} }' },
      { path: 'Util.java', content: 'class Util {}' },
    ]
    expect(mainFilePath(files)).toBe('Main.java')
    expect(mainFilePath([files[2]])).toBeNull()
    expect(initialFiles({ starterCode: 'public class Calculadora {}' })).toEqual([{ path: 'Calculadora.java', content: 'public class Calculadora {}' }])
    expect(initialFiles({ starterFiles: [{ path: 'A.java', content: 'x' }], starterCode: 'y' })).toEqual([{ path: 'A.java', content: 'x' }])
    expect(initialFiles({ starterCode: '' })[0].path).toBe('Main.java')
    expect(fileNameFor('enum Cor { A }')).toBe('Cor.java')
  })

  it('junta os arquivos para a tutora e acha as linhas da exceção', () => {
    const files = [{ path: 'Main.java', content: 'a' }, { path: 'loja/Pedido.java', content: 'b' }]
    expect(filesToText(files)).toBe('// Arquivo: Main.java\na\n\n// Arquivo: loja/Pedido.java\nb')
    const trace = 'Exception in thread "main" java.lang.IllegalStateException\n\tat loja.Pedido.fechar(Pedido.java:12)\n\tat Main.main(Main.java:5)\n\tat java.base/java.util.Scanner.next(Scanner.java:1)'
    const locations = stackLocations(trace, files)
    expect(locations.map((l) => [l.path, l.line])).toEqual([['loja/Pedido.java', 12], ['Main.java', 5]])
    expect(trace.slice(locations[0].index, locations[0].index + locations[0].length)).toBe('Pedido.java:12')
  })

  it('usa o editor só para exercícios de código Java', () => {
    expect(isJavaProjectExercise({ language: 'JAVA', exerciseType: 'CODE_FIX' })).toBe(true)
    expect(isJavaProjectExercise({ language: null, starterCode: 'public class SolucaoPratica {}' })).toBe(true)
    expect(isJavaProjectExercise({ language: 'JAVA', exerciseType: 'SQL_QUERY' })).toBe(false)
    expect(isJavaProjectExercise({ language: 'OTHER', starterCode: 'class X {}' })).toBe(false)
  })
})
