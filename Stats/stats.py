import argparse
import jellyfish


def main():
    parser = argparse.ArgumentParser(description='Compare two file and get the percentage.')
    parser.add_argument('-f1', '--file1', action='store', dest='f1', required=True,
                        help='Specify the input path of the first file.')
    parser.add_argument('-f2', '--file2', action='store', dest='f2', required=True,
                        help='Specify the input path of the second file.')

    args = parser.parse_args()

    text1 = open(args.f1).read()
    text2 = open(args.f2).read()
    ratio = jellyfish.jaro_distance(text1, text2)
    print(round(ratio, 2))


if __name__ == '__main__':
    main()
