interface FormInputProps {
  label: string;
  name: string;
  type?: 'text' | 'email' | 'password' | 'tel';
  placeholder?: string;
  required?: boolean;
  value?: string;
  onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void;
}

interface FormTextareaProps {
  label: string;
  name: string;
  placeholder?: string;
  required?: boolean;
  value?: string;
  onChange?: (e: React.ChangeEvent<HTMLTextAreaElement>) => void;
  rows?: number;
}

interface FormSelectProps {
  label: string;
  name: string;
  options: { value: string; label: string }[];
  required?: boolean;
  value?: string;
  onChange?: (e: React.ChangeEvent<HTMLSelectElement>) => void;
}

const labelClasses =
  'block font-ui text-[0.85rem] font-semibold text-foreground-dark mb-2';

const inputClasses =
  'w-full font-ui text-[0.95rem] px-4 py-3 border-[1.5px] border-border rounded-md bg-background text-foreground-dark outline-none transition-all duration-200 focus:border-secondary focus:shadow-[0_0_0_3px_var(--color-secondary-pale)] placeholder:text-foreground-muted';

export function FormInput({
  label,
  name,
  type = 'text',
  placeholder,
  required = false,
  value,
  onChange,
}: FormInputProps) {
  return (
    <div className="mb-6">
      <label htmlFor={name} className={labelClasses}>
        {label}
      </label>
      <input
        id={name}
        name={name}
        type={type}
        placeholder={placeholder}
        required={required}
        value={value}
        onChange={onChange}
        className={inputClasses}
      />
    </div>
  );
}

export function FormTextarea({
  label,
  name,
  placeholder,
  required = false,
  value,
  onChange,
  rows = 4,
}: FormTextareaProps) {
  return (
    <div className="mb-6">
      <label htmlFor={name} className={labelClasses}>
        {label}
      </label>
      <textarea
        id={name}
        name={name}
        placeholder={placeholder}
        required={required}
        value={value}
        onChange={onChange}
        rows={rows}
        className={`${inputClasses} min-h-[100px] resize-y`}
      />
    </div>
  );
}

export function FormSelect({
  label,
  name,
  options,
  required = false,
  value,
  onChange,
}: FormSelectProps) {
  return (
    <div className="mb-6">
      <label htmlFor={name} className={labelClasses}>
        {label}
      </label>
      <select
        id={name}
        name={name}
        required={required}
        value={value}
        onChange={onChange}
        className={inputClasses}
      >
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
    </div>
  );
}
